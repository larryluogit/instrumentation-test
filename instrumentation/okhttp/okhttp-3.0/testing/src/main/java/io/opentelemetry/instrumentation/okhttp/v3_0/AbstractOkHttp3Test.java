/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpMethod;
import org.junit.jupiter.api.Test;

public abstract class AbstractOkHttp3Test extends AbstractHttpClientTest<Request> {

  protected abstract Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder);

  protected final Call.Factory client = createCallFactory(getClientBuilder(false));
  private final Call.Factory clientWithReadTimeout = createCallFactory(getClientBuilder(true));

  protected OkHttpClient.Builder getClientBuilder(boolean withReadTimeout) {
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .protocols(singletonList(Protocol.HTTP_1_1));
    if (withReadTimeout) {
      builder
          // don't want retries on time outs
          .retryOnConnectionFailure(false)
          .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }
    return builder;
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    RequestBody body =
        HttpMethod.requiresRequestBody(method)
            ? RequestBody.create(MediaType.parse("text/plain"), "")
            : null;
    return new Request.Builder()
        .url(uri.toURL())
        .method(method, body)
        .headers(Headers.of(headers))
        .build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    Response response = getClient(uri).newCall(request).execute();
    try (ResponseBody ignored = response.body()) {
      return response.code();
    }
  }

  @Override
  public void sendRequestWithCallback(
      Request request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult httpClientResult) {
    getClient(uri)
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(Call call, IOException e) {
                httpClientResult.complete(e);
              }

              @Override
              public void onResponse(Call call, Response response) {
                try (ResponseBody ignored = response.body()) {
                  httpClientResult.complete(response.code());
                }
              }
            });
  }

  private Call.Factory getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    // TODO: replace the base class redirect tests
    optionsBuilder.disableTestRedirects();

    optionsBuilder.enableTestReadTimeout();

    optionsBuilder.setHttpAttributes(
        uri -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpClientTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          // the tests are capturing the user-agent, but since it's not possible to override it in
          // the builder, and since it contains the okhttp library version, let's just skip
          // verification on this attribute
          attributes.remove(SemanticAttributes.USER_AGENT_ORIGINAL);

          // protocol is extracted from the response, and those URLs cause exceptions (= null
          // response)
          if ("http://localhost:61/".equals(uri.toString())
              || "https://192.0.2.1/".equals(uri.toString())
              || resolveAddress("/read-timeout").toString().equals(uri.toString())) {
            attributes.remove(stringKey("net.protocol.name"));
            attributes.remove(stringKey("net.protocol.version"));
          }

          return attributes;
        });
  }

  private static class TestInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
      // make copy of the request
      Request request = chain.request().newBuilder().build();

      return chain.proceed(request);
    }
  }

  @Test
  void requestWithCustomInterceptor() throws Throwable {
    String method = "GET";
    URI uri = resolveAddress("/success");

    HttpClientResult result = new HttpClientResult(() -> testing.runWithSpan("child", () -> {}));
    OkHttpClient.Builder builder = getClientBuilder(false);
    builder.addInterceptor(new TestInterceptor());
    Call.Factory testClient = createCallFactory(builder);

    testing.runWithSpan(
        "parent",
        () -> {
          Request request = buildRequest(method, uri, Collections.emptyMap());
          testClient
              .newCall(request)
              .enqueue(
                  new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                      result.complete(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                      try (ResponseBody ignored = response.body()) {
                        result.complete(response.code());
                      }
                    }
                  });
        });

    assertThat(result.get()).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0)),
              span ->
                  span.hasName("test-http-server")
                      .hasKind(SpanKind.SERVER)
                      .hasParent(trace.getSpan(1)),
              span -> span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)));
        });
  }

  // TODO: replace the resend tests in the AbstractHttpClient with these

  @Test
  void new_basicRequestWith1Redirect() throws Exception {
    URI uri = resolveAddress("/redirect");

    int responseCode = sendRequest(buildRequest("GET", uri, emptyMap()), "GET", uri, emptyMap());

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString()),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(
                            SemanticAttributes.HTTP_URL, uri.resolve("/success").toString())
                        .hasAttribute(SemanticAttributes.HTTP_RESEND_COUNT, 1L),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void new_basicRequestWith2Redirects() throws Exception {
    URI uri = resolveAddress("/another-redirect");

    int responseCode = sendRequest(buildRequest("GET", uri, emptyMap()), "GET", uri, emptyMap());

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString()),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(
                            SemanticAttributes.HTTP_URL, uri.resolve("/redirect").toString())
                        .hasAttribute(SemanticAttributes.HTTP_RESEND_COUNT, 1L),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(
                            SemanticAttributes.HTTP_URL, uri.resolve("/success").toString())
                        .hasAttribute(SemanticAttributes.HTTP_RESEND_COUNT, 2L),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  static final String BASIC_AUTH_KEY = "custom-authorization-header";
  static final String BASIC_AUTH_VAL = "plain text auth token";

  @Test
  void new_redirectToSecuredCopiesAuthHeader() throws Exception {
    URI uri = resolveAddress("/to-secured");
    Map<String, String> headers = singletonMap(BASIC_AUTH_KEY, BASIC_AUTH_VAL);

    int responseCode = sendRequest(buildRequest("GET", uri, headers), "GET", uri, headers);

    assertThat(responseCode).isEqualTo(200);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString()),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttribute(
                            SemanticAttributes.HTTP_URL, uri.resolve("/secured").toString())
                        .hasAttribute(SemanticAttributes.HTTP_RESEND_COUNT, 1L),
                span ->
                    span.hasName("test-http-server")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  // TODO: add basic auth scenario

  @Test
  void new_circularRedirects() {
    URI uri = resolveAddress("/circular-redirect");

    Throwable thrown =
        catchThrowable(
            () -> sendRequest(buildRequest("GET", uri, emptyMap()), "GET", uri, emptyMap()));
    assertThat(thrown).isNotNull();

    int maxNumberOfResends = 21; // 1st send + 20 retries
    testing.waitAndAssertTraces(
        IntStream.range(0, maxNumberOfResends)
            .mapToObj(i -> makeCircularRedirectAssert(uri, i))
            .collect(Collectors.toList()));
  }

  private static Consumer<TraceAssert> makeCircularRedirectAssert(URI uri, int resendNo) {
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span -> {
              span.hasName("GET")
                  .hasKind(SpanKind.CLIENT)
                  .hasNoParent()
                  .hasAttribute(SemanticAttributes.HTTP_URL, uri.toString());
              if (resendNo > 0) {
                span.hasAttribute(SemanticAttributes.HTTP_RESEND_COUNT, (long) resendNo);
              }
            },
            span ->
                span.hasName("test-http-server")
                    .hasKind(SpanKind.SERVER)
                    .hasParent(trace.getSpan(0)));
  }
}

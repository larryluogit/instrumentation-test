/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableSet;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.util.ThrowingRunnable;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.ExceptionAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.semconv.UserAgentAttributes;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class UndertowServerTest extends AbstractHttpServerTest<Undertow> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public Undertow setupServer() {
    Undertow.Builder builder =
        Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(
                Handlers.path()
                    .addExactPath(
                        SUCCESS.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> exchange.getResponseSender().send(SUCCESS.getBody())))
                    .addExactPath(
                        QUERY_PARAM.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () ->
                                        exchange
                                            .getResponseSender()
                                            .send(exchange.getQueryString())))
                    .addExactPath(
                        REDIRECT.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> {
                                      exchange.setStatusCode(StatusCodes.FOUND);
                                      exchange
                                          .getResponseHeaders()
                                          .put(Headers.LOCATION, REDIRECT.getBody());
                                      exchange.endExchange();
                                    }))
                    .addExactPath(
                        CAPTURE_HEADERS.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> {
                                      exchange.setStatusCode(StatusCodes.OK);
                                      exchange
                                          .getResponseHeaders()
                                          .put(
                                              new HttpString("X-Test-Response"),
                                              exchange
                                                  .getRequestHeaders()
                                                  .getFirst("X-Test-Request"));
                                      exchange.getResponseSender().send(CAPTURE_HEADERS.getBody());
                                    }))
                    .addExactPath(
                        ERROR.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> {
                                      exchange.setStatusCode(ERROR.getStatus());
                                      exchange.getResponseSender().send(ERROR.getBody());
                                    }))
                    .addExactPath(
                        EXCEPTION.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> {
                                      throw new Exception(EXCEPTION.getBody());
                                    }))
                    .addExactPath(
                        INDEXED_CHILD.rawPath(),
                        exchange ->
                            testing.runWithSpan(
                                "controller",
                                (ThrowingRunnable<Exception>)
                                    () -> {
                                      INDEXED_CHILD.collectSpanAttributes(
                                          name ->
                                              exchange.getQueryParameters().get(name).peekFirst());
                                      exchange.getResponseSender().send(INDEXED_CHILD.getBody());
                                    }))
                    .addExactPath(
                        "sendResponse",
                        exchange -> {
                          Span.current().addEvent("before-event");
                          testing.runWithSpan(
                              "sendResponse",
                              () -> {
                                exchange.setStatusCode(StatusCodes.OK);
                                exchange.getResponseSender().send("sendResponse");
                              });
                          // event is added only when server span has not been ended
                          // we need to make sure that sending response does not end server span
                          Span.current().addEvent("after-event");
                        })
                    .addExactPath(
                        "sendResponseWithException",
                        exchange -> {
                          Span.current().addEvent("before-event");
                          testing.runWithSpan(
                              "sendResponseWithException",
                              () -> {
                                exchange.setStatusCode(StatusCodes.OK);
                                exchange.getResponseSender().send("sendResponseWithException");
                              });
                          // event is added only when server span has not been ended
                          // we need to make sure that sending response does not end server span
                          Span.current().addEvent("after-event");
                          throw new Exception("exception after sending response");
                        }));
    configureUndertow(builder);
    Undertow server = builder.build();
    server.start();
    return server;
  }

  @Override
  public void stopServer(Undertow undertow) {
    undertow.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHttpAttributes(endpoint -> ImmutableSet.of(NetworkAttributes.NETWORK_PEER_PORT));
    options.setHasResponseCustomizer(serverEndpoint -> true);
    options.setUseHttp2(useHttp2());
  }

  protected void configureUndertow(Undertow.Builder builder) {}

  protected boolean useHttp2() {
    return false;
  }

  @DisplayName("test send response")
  @Test
  void testSendResponse() {
    URI uri = address.resolve("sendResponse");
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("sendResponse");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("before-event"),
                            event -> event.hasName("after-event"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ClientAttributes.CLIENT_ADDRESS, TEST_CLIENT_IP),
                            equalTo(UrlAttributes.URL_SCHEME, uri.getScheme()),
                            equalTo(UrlAttributes.URL_PATH, uri.getPath()),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(UserAgentAttributes.USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(
                                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                                useHttp2() ? "2" : "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                k -> k.isInstanceOf(Long.class))),
                span ->
                    span.hasName("sendResponse")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  @DisplayName("test send response with exception")
  void testSendResponseWithException() {
    URI uri = address.resolve("sendResponseWithException");
    AggregatedHttpResponse response = client.get(uri.toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8().trim()).isEqualTo("sendResponseWithException");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasNoParent()
                        .hasKind(SpanKind.SERVER)
                        .hasEventsSatisfyingExactly(
                            event -> event.hasName("before-event"),
                            event -> event.hasName("after-event"),
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            Exception.class.getName()),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_MESSAGE,
                                            "exception after sending response"),
                                        satisfies(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(ClientAttributes.CLIENT_ADDRESS, TEST_CLIENT_IP),
                            equalTo(UrlAttributes.URL_SCHEME, uri.getScheme()),
                            equalTo(UrlAttributes.URL_PATH, uri.getPath()),
                            equalTo(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(UserAgentAttributes.USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(
                                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                                useHttp2() ? "2" : "1.1"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, uri.getHost()),
                            equalTo(ServerAttributes.SERVER_PORT, uri.getPort()),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                k -> k.isInstanceOf(Long.class))),
                span ->
                    span.hasName("sendResponseWithException")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}

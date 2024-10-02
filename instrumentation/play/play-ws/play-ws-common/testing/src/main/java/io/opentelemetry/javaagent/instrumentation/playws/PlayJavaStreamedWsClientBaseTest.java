/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import play.libs.ws.StandaloneWSClient;
import play.libs.ws.StandaloneWSRequest;
import play.libs.ws.StandaloneWSResponse;
import play.libs.ws.ahc.StandaloneAhcWSClient;

class PlayJavaStreamedWsClientBaseTest extends PlayWsClientBaseTest<StandaloneWSRequest> {

  private static StandaloneWSClient wsClient;
  private static StandaloneWSClient wsClientWithReadTimeout;

  @BeforeEach
  @Override
  void setup() {
    super.setup();
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer);
    wsClientWithReadTimeout =
        new StandaloneAhcWSClient(asyncHttpClientWithReadTimeout, materializer);
    autoCleanup.deferCleanup(wsClient);
    autoCleanup.deferCleanup(wsClientWithReadTimeout);
  }

  @AfterEach
  @Override
  void tearDown() throws IOException {
    if (wsClient != null) {
      wsClient.close();
    }
    if (wsClientWithReadTimeout != null) {
      wsClientWithReadTimeout.close();
    }
    super.tearDown();
  }

  @Override
  public StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    StandaloneWSRequest request = getClient(uri).url(uri.toString()).setFollowRedirects(true);
    headers.forEach(request::addHeader);
    request.setMethod(method);
    return request;
  }

  @Override
  public int sendRequest(
      StandaloneWSRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return internalSendRequest(request).toCompletableFuture().get().getStatus();
  }

  @Override
  public void sendRequestWithCallback(
      StandaloneWSRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    internalSendRequest(request)
        .whenComplete(
            (response, throwable) -> {
              if (throwable != null) {
                requestResult.complete(throwable.getCause());
              } else {
                requestResult.complete(response.getStatus());
              }
            });
  }

  private CompletionStage<StandaloneWSResponse> internalSendRequest(StandaloneWSRequest request) {
    CompletionStage<? extends StandaloneWSResponse> stream = request.stream();
    // The status can be ready before the body so explicitly call wait for body to be ready
    return stream
        .thenCompose(
            response -> response.getBodyAsSource().runFold("", (acc, out) -> "", materializer))
        .thenCombine(stream, (body, response) -> response);
  }

  private StandaloneWSClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return wsClientWithReadTimeout;
    }
    return wsClient;
  }
}
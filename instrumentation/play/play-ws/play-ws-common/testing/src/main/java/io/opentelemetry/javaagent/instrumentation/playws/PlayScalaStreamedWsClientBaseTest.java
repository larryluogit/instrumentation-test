/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import play.api.libs.ws.StandaloneWSClient;
import play.api.libs.ws.StandaloneWSRequest;
import play.api.libs.ws.StandaloneWSResponse;
import play.api.libs.ws.ahc.StandaloneAhcWSClient;
import scala.Function1;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.jdk.javaapi.CollectionConverters;
import scala.util.Try;

public class PlayScalaStreamedWsClientBaseTest extends PlayWsClientBaseTest<StandaloneWSRequest> {

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
  public StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    return getClient(uri)
        .url(uri.toURL().toString())
        .withMethod(method)
        .withFollowRedirects(true)
        .withHttpHeaders(CollectionConverters.asScala(headers).toList());
  }

  @Override
  public int sendRequest(
      StandaloneWSRequest request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return Await.result(internalSendRequest(request), Duration.apply(10, TimeUnit.SECONDS))
        .status();
  }

  @Override
  public void sendRequestWithCallback(
      StandaloneWSRequest request,
      String method,
      URI uri,
      Map<String, String> headers,
      HttpClientResult requestResult) {
    internalSendRequest(request)
        .onComplete(
            new Function1<Try<StandaloneWSResponse>, Void>() {
              @Override
              public Void apply(Try<StandaloneWSResponse> response) {
                if (response.isSuccess()) {
                  requestResult.complete(response.get().status());
                } else {
                  requestResult.complete(response.failed().get());
                }
                return null;
              }
            },
            ExecutionContext.global());
  }

  private static Future<StandaloneWSResponse> internalSendRequest(StandaloneWSRequest request) {
    Future<StandaloneWSResponse> futureResponse = request.stream();
    // The status can be ready before the body so explicitly call wait for body to be ready
    Future<String> bodyResponse =
        futureResponse.flatMap(
            new Function1<StandaloneWSResponse, Future<String>>() {
              @Override
              public Future<String> apply(StandaloneWSResponse wsResponse) {
                return wsResponse.bodyAsSource().runFold("", (acc, out) -> "", materializer);
              }
            },
            ExecutionContext.global());
    return bodyResponse.flatMap(
        new Function1<String, Future<StandaloneWSResponse>>() {
          @Override
          public Future<StandaloneWSResponse> apply(String v1) {
            return futureResponse;
          }
        },
        ExecutionContext.global());
  }

  private static StandaloneWSClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return wsClientWithReadTimeout;
    }
    return wsClient;
  }
}

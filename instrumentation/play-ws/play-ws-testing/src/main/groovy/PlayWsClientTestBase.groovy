/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import play.libs.ws.StandaloneWSClient
import play.libs.ws.StandaloneWSRequest
import play.libs.ws.StandaloneWSResponse
import play.libs.ws.ahc.StandaloneAhcWSClient
import scala.Function1
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try
import spock.lang.Shared

class PlayJavaWsClientTestBase extends PlayWsClientTestBaseBase<StandaloneWSRequest> {
  @Shared
  StandaloneWSClient wsClient

  @Override
  StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)
    headers.entrySet().each { entry -> request.addHeader(entry.getKey(), entry.getValue()) }
    return request.setMethod(method)
  }

  @Override
  int sendRequest(StandaloneWSRequest request, String method, URI uri, Map<String, String> headers) {
    return request.execute().toCompletableFuture().get().status
  }

  @Override
  void sendRequestWithCallback(StandaloneWSRequest request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    request.execute().thenAccept {
      callback.accept(it.status)
    }
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayJavaStreamedWsClientTestBase extends PlayWsClientTestBaseBase<StandaloneWSRequest> {
  @Shared
  StandaloneWSClient wsClient

  @Override
  StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)
    headers.entrySet().each { entry -> request.addHeader(entry.getKey(), entry.getValue()) }
    request.setMethod(method)
    return request
  }

  @Override
  int sendRequest(StandaloneWSRequest request, String method, URI uri, Map<String, String> headers) {
    return internalSendRequest(request).toCompletableFuture().get().status
  }

  @Override
  void sendRequestWithCallback(StandaloneWSRequest request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    internalSendRequest(request).thenAccept {
      callback.accept(it.status)
    }
  }

  private CompletionStage<StandaloneWSResponse> internalSendRequest(StandaloneWSRequest request) {
    def stream = request.stream()
    // The status can be ready before the body so explicitly call wait for body to be ready
    return stream
      .thenCompose { StandaloneWSResponse response ->
        response.getBodyAsSource().runFold("", { acc, out -> "" }, materializer)
      }
      .thenCombine(stream) { String body, StandaloneWSResponse response ->
        response
      }
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayScalaWsClientTestBase extends PlayWsClientTestBaseBase<play.api.libs.ws.StandaloneWSRequest> {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  play.api.libs.ws.StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
  }

  @Override
  int sendRequest(play.api.libs.ws.StandaloneWSRequest request, String method, URI uri, Map<String, String> headers) {
    def futureResponse = request.execute()
    def response = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))
    return response.status()
  }

  @Override
  void sendRequestWithCallback(play.api.libs.ws.StandaloneWSRequest request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    request.execute().onComplete(new Function1<Try<play.api.libs.ws.StandaloneWSResponse>, Void>() {
      @Override
      Void apply(Try<play.api.libs.ws.StandaloneWSResponse> response) {
        callback.accept(response.get().status())
        return null
      }
    }, ExecutionContext.global())
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayScalaStreamedWsClientTestBase extends PlayWsClientTestBaseBase<play.api.libs.ws.StandaloneWSRequest> {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  play.api.libs.ws.StandaloneWSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
  }

  @Override
  int sendRequest(play.api.libs.ws.StandaloneWSRequest request, String method, URI uri, Map<String, String> headers) {
    Await.result(internalSendRequest(request), Duration.apply(5, TimeUnit.SECONDS)).status()
  }

  @Override
  void sendRequestWithCallback(play.api.libs.ws.StandaloneWSRequest request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    internalSendRequest(request).onComplete(new Function1<Try<play.api.libs.ws.StandaloneWSResponse>, Void>() {
      @Override
      Void apply(Try<play.api.libs.ws.StandaloneWSResponse> response) {
        callback.accept(response.get().status())
        return null
      }
    }, ExecutionContext.global())
  }

  private Future<play.api.libs.ws.StandaloneWSResponse> internalSendRequest(play.api.libs.ws.StandaloneWSRequest request) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = request.stream()
    // The status can be ready before the body so explicitly call wait for body to be ready
    Future<String> bodyResponse = futureResponse.flatMap(new Function1<play.api.libs.ws.StandaloneWSResponse, Future<String>>() {
      @Override
      Future<String> apply(play.api.libs.ws.StandaloneWSResponse wsResponse) {
        return wsResponse.bodyAsSource().runFold("", { acc, out -> "" }, materializer)
      }
    }, ExecutionContext.global())
    return bodyResponse.flatMap(new Function1<String, Future<play.api.libs.ws.StandaloneWSResponse>>() {
      @Override
      Future<play.api.libs.ws.StandaloneWSResponse> apply(String v1) {
        return futureResponse
      }
    }, ExecutionContext.global())
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

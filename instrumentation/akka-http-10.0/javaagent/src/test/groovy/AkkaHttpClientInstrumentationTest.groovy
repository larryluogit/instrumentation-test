/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT

import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpMethods
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.function.Consumer
import spock.lang.Shared

class AkkaHttpClientInstrumentationTest extends HttpClientTest<HttpRequest> implements AgentTestTrait {

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  @Override
  HttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    return HttpRequest.create(uri.toString())
      .withMethod(HttpMethods.lookup(method).get())
      .addHeaders(headers.collect { RawHeader.create(it.key, it.value) })
  }

  @Override
  int sendRequest(HttpRequest request, String method, URI uri, Map<String, String> headers) {
    return Http.get(system)
      .singleRequest(request, materializer)
      .toCompletableFuture()
      .get()
      .status()
      .intValue()
  }

  @Override
  void sendRequestWithCallback(HttpRequest request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    Http.get(system).singleRequest(request, materializer).thenAccept {
      callback.accept(it.status().intValue())
    }
  }

  // TODO(anuraaga): Context leak seems to prevent us from running asynchronous tests in a row.
  // Disable for now.
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2639
  @Override
  boolean testCallback() {
    false
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // Not sure how to properly set timeouts...
    return false
  }

  @Override
  boolean testCausality() {
    false
  }

  def "singleRequest exception trace"() {
    when:
    // Passing null causes NPE in singleRequest
    Http.get(system).singleRequest(null, materializer)

    then:
    def e = thrown NullPointerException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          hasNoParent()
          name "HTTP request"
          kind CLIENT
          errored true
          errorEvent(NullPointerException, e.getMessage())
        }
      }
    }
  }
}

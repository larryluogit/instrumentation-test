/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.api.semconv.network.internal.NetworkAttributes
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.HttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpRequestBuilder
import io.vertx.reactivex.core.Vertx
import spock.lang.Shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

import static VertxReactiveWebServer.TEST_REQUEST_ID_ATTRIBUTE
import static VertxReactiveWebServer.TEST_REQUEST_ID_PARAMETER
import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

class VertxReactivePropagationTest extends AgentInstrumentationSpecification {
  @Shared
  WebClient client

  @Shared
  int port

  @Shared
  Vertx server

  def setupSpec() {
    port = PortUtils.findOpenPort()
    server = VertxReactiveWebServer.start(port)
    client = WebClient.of("h1c://localhost:${port}")
  }

  def cleanupSpec() {
    server.close()
  }

  //Verifies that context is correctly propagated and sql query span has correct parent.
  //Tests io.opentelemetry.javaagent.instrumentation.vertx.reactive.VertxRxInstrumentation
  def "should propagate context over vert.x rx-java framework"() {
    setup:
    def response = client.get("/listProducts").aggregate().join()

    expect:
    response.status().code() == SUCCESS.status

    and:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "GET /listProducts"
          kind SERVER
          hasNoParent()
          attributes {
            "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$NetworkAttributes.NETWORK_PEER_ADDRESS" "127.0.0.1"
            "$NetworkAttributes.NETWORK_PEER_PORT" Long
            "$SemanticAttributes.SERVER_ADDRESS" "localhost"
            "$SemanticAttributes.SERVER_PORT" Long
            "$SemanticAttributes.URL_PATH" "/listProducts"
            "$SemanticAttributes.HTTP_REQUEST_METHOD" "GET"
            "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$SemanticAttributes.URL_SCHEME" "http"
            "$SemanticAttributes.USER_AGENT_ORIGINAL" String
            "$SemanticAttributes.HTTP_ROUTE" "/listProducts"
          }
        }
        span(1) {
          name "handleListProducts"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(2) {
          name "listProducts"
          kind SpanKind.INTERNAL
          childOf span(1)
        }
        span(3) {
          name "SELECT test.products"
          kind CLIENT
          childOf span(2)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "hsqldb"
            "$SemanticAttributes.DB_NAME" "test"
            "$SemanticAttributes.DB_USER" "SA"
            "$SemanticAttributes.DB_CONNECTION_STRING" "hsqldb:mem:"
            "$SemanticAttributes.DB_STATEMENT" "SELECT id, name, price, weight FROM products"
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "products"
          }
        }
      }
    }
  }

  def "should propagate context correctly over vert.x rx-java framework with high concurrency"() {
    setup:
    int count = 100
    def baseUrl = "/listProducts"
    def latch = new CountDownLatch(1)

    def pool = Executors.newFixedThreadPool(8)
    def propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
    def setter = { HttpRequestBuilder carrier, String name, String value ->
      carrier.header(name, value)
    }

    when:
    count.times { index ->
      def job = {
        latch.await()
        runWithSpan("client " + index) {
          HttpRequestBuilder builder = HttpRequest.builder()
            .get("${baseUrl}?${TEST_REQUEST_ID_PARAMETER}=${index}")
          Span.current().setAttribute(TEST_REQUEST_ID_ATTRIBUTE, index)
          propagator.inject(Context.current(), builder, setter)
          client.execute(builder.build()).aggregate().join()
        }
      }
      pool.submit(job)
    }

    latch.countDown()

    then:
    assertTraces(count) {
      (0..count - 1).each {
        trace(it, 5) {
          def rootSpan = it.span(0)
          def requestId = Long.valueOf(rootSpan.name.substring("client ".length()))

          span(0) {
            name "client $requestId"
            kind SpanKind.INTERNAL
            hasNoParent()
            attributes {
              "${TEST_REQUEST_ID_ATTRIBUTE}" requestId
            }
          }
          span(1) {
            name "GET /listProducts"
            kind SERVER
            childOf(span(0))
            attributes {
              "$SemanticAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
              "$NetworkAttributes.NETWORK_PEER_ADDRESS" "127.0.0.1"
              "$NetworkAttributes.NETWORK_PEER_PORT" Long
              "$SemanticAttributes.SERVER_ADDRESS" "localhost"
              "$SemanticAttributes.SERVER_PORT" Long
              "$SemanticAttributes.URL_PATH" baseUrl
              "$SemanticAttributes.URL_QUERY" "$TEST_REQUEST_ID_PARAMETER=$requestId"
              "$SemanticAttributes.HTTP_REQUEST_METHOD" "GET"
              "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 200
              "$SemanticAttributes.URL_SCHEME" "http"
              "$SemanticAttributes.USER_AGENT_ORIGINAL" String
              "$SemanticAttributes.HTTP_ROUTE" "/listProducts"
              "${TEST_REQUEST_ID_ATTRIBUTE}" requestId
            }
          }
          span(2) {
            name "handleListProducts"
            kind SpanKind.INTERNAL
            childOf(span(1))
            attributes {
              "${TEST_REQUEST_ID_ATTRIBUTE}" requestId
            }
          }
          span(3) {
            name "listProducts"
            kind SpanKind.INTERNAL
            childOf(span(2))
            attributes {
              "${TEST_REQUEST_ID_ATTRIBUTE}" requestId
            }
          }
          span(4) {
            name "SELECT test.products"
            kind CLIENT
            childOf(span(3))
            attributes {
              "$SemanticAttributes.DB_SYSTEM" "hsqldb"
              "$SemanticAttributes.DB_NAME" "test"
              "$SemanticAttributes.DB_USER" "SA"
              "$SemanticAttributes.DB_CONNECTION_STRING" "hsqldb:mem:"
              "$SemanticAttributes.DB_STATEMENT" "SELECT id AS request$requestId, name, price, weight FROM products"
              "$SemanticAttributes.DB_OPERATION" "SELECT"
              "$SemanticAttributes.DB_SQL_TABLE" "products"
            }
          }
        }
      }
    }

    cleanup:
    pool.shutdownNow()
  }
}

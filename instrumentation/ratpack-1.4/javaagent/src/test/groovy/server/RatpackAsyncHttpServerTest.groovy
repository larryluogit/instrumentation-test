/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import io.opentelemetry.api.trace.Span
import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp

class RatpackAsyncHttpServerTest extends RatpackHttpServerTest {

  @Override
  EmbeddedApp startServer(int bindPort) {
    def ratpack = GroovyEmbeddedApp.ratpack {
      serverConfig {
        port bindPort
        address InetAddress.getByName('localhost')
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.rawPath()) {
          all {
            Promise.sync {
              SUCCESS
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(INDEXED_CHILD.rawPath()) {
          all {
            Promise.sync {
              INDEXED_CHILD
            } then {
              controller(INDEXED_CHILD) {
                Span.current().setAttribute("test.request.id", request.queryParams.get("id") as long)
                context.response.status(INDEXED_CHILD.status).send()
              }
            }
          }
        }
        prefix(QUERY_PARAM.rawPath()) {
          all {
            Promise.sync {
              QUERY_PARAM
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(request.query)
              }
            }
          }
        }
        prefix(REDIRECT.rawPath()) {
          all {
            Promise.sync {
              REDIRECT
            } then { endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        prefix(ERROR.rawPath()) {
          all {
            Promise.sync {
              ERROR
            } then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(EXCEPTION.rawPath()) {
          all {
            Promise.sync {
              EXCEPTION
            } then { endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
        prefix("path/:id/param") {
          all {
            Promise.sync {
              PATH_PARAM
            }.fork().then { endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(pathTokens.id)
              }
            }
          }
        }
      }
    }
    ratpack.server.start()

    assert ratpack.address.port == bindPort
    assert ratpack.server.bindHost == 'localhost'
    return ratpack
  }
}

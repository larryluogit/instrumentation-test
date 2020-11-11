/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import javax.servlet.DispatcherType
import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.resource.FileResource

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM

class Struts2ActionSpanTest extends HttpServerTest<Server> {

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  boolean testPathParam() {
    return false
  }

  @Override
  boolean testExceptionBody() {
    return false
  }

  @Override
  boolean testError() {
    return false
  }

  @Override
  boolean testRedirect() {
    return false
  }

  @Override
  boolean hasHandlerSpan() {
    return true
  }

  String expectedControllerName(ServerEndpoint serverEndpoint) {
    switch (serverEndpoint) {
      case QUERY_PARAM: return "GreetingAction.query"
      case EXCEPTION: return "GreetingAction.exception"
      default: return "GreetingAction.success"
    }
  }

  String expectedMethodName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case QUERY_PARAM: return "query"
      case EXCEPTION: return "exception"
      default: return "success"
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    trace.span(index) {
      name expectedControllerName(endpoint)
      kind Span.Kind.INTERNAL
      errored endpoint == EXCEPTION
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
      attributes {
        'code.namespace' "GreetingAction"
        'code.function' { it == expectedMethodName(endpoint) }
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  Server startServer(int port) {
    def server = new Server(port)
    ServletContextHandler context = new ServletContextHandler(0);
    context.setContextPath("/")
    def resource = new FileResource(getClass().getResource("/"))
    context.setBaseResource(resource)
    server.setHandler(context)

    context.addServlet(DefaultServlet.class, "/")
    context.addFilter(StrutsPrepareAndExecuteFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST))

    server.start()

    return server
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }
}

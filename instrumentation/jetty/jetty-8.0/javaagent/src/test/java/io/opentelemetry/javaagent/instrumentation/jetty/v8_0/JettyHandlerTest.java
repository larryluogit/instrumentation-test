/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Sets;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.junit.jupiter.api.extension.RegisterExtension;

public class JettyHandlerTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private static final ErrorHandler errorHandler =
      new ErrorHandler() {
        @Override
        protected void handleErrorPage(
            HttpServletRequest request, Writer writer, int code, String message)
            throws IOException {
          Throwable th = (Throwable) request.getAttribute("javax.servlet.error.exception");
          String errorMsg = th != null ? th.getMessage() : message;
          if (errorMsg != null) {
            writer.write(errorMsg);
          }
        }
      };

  private static final TestHandler testHandler = new TestHandler();

  @Override
  protected Server setupServer() {
    Server server = new Server(port);
    server.setHandler(testHandler);
    server.addBean(errorHandler);
    try {
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return server;
  }

  @Override
  protected void stopServer(Server server) {
    try {
      server.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        unused ->
            Sets.difference(
                DEFAULT_HTTP_ATTRIBUTES, Collections.singleton(SemanticAttributes.HTTP_ROUTE)));
    options.setHasResponseSpan(endpoint -> endpoint == REDIRECT || endpoint == ERROR);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));
    options.setHasResponseCustomizer(endpoint -> endpoint != EXCEPTION);
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (endpoint == REDIRECT) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"));
    } else if (endpoint == ERROR) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
    }
    span.hasKind(SpanKind.INTERNAL).hasAttributesSatisfying(Attributes::isEmpty);
    return span;
  }

  private static void handleRequest(Request request, HttpServletResponse response) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(request.getRequestURI());
    controller(
        endpoint,
        () -> {
          try {
            return response(request, response, endpoint);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static HttpServletResponse response(
      Request request, HttpServletResponse response, ServerEndpoint endpoint) throws IOException {
    response.setContentType("text/plain");
    switch (endpoint) {
      case SUCCESS:
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      case QUERY_PARAM:
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(request.getQueryString());
        break;
      case REDIRECT:
        response.sendRedirect(endpoint.getBody());
        break;
      case ERROR:
        response.sendError(endpoint.getStatus(), endpoint.getBody());
        break;
      case CAPTURE_HEADERS:
        response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"));
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      case EXCEPTION:
        throw new IllegalStateException(endpoint.getBody());
      case INDEXED_CHILD:
        INDEXED_CHILD.collectSpanAttributes(name -> request.getParameter(name));
        response.setStatus(endpoint.getStatus());
        response.getWriter().print(endpoint.getBody());
        break;
      default:
        response.setStatus(NOT_FOUND.getStatus());
        response.getWriter().print(NOT_FOUND.getBody());
        break;
    }
    return response;
  }

  private static class TestHandler extends AbstractHandler {
    @Override
    public void handle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException, ServletException {
      // This line here is to verify that we don't break Jetty if it wants to cast to implementation
      // class
      Response jettyResponse = (Response) response;
      if (baseRequest.getDispatcherType() != DispatcherType.ERROR) {
        handleRequest(baseRequest, jettyResponse);
        baseRequest.setHandled(true);
      } else {
        errorHandler.handle(target, baseRequest, baseRequest, response);
      }
    }
  }
}

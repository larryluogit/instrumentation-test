/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Servlet3Decorator
    extends HttpServerDecorator<HttpServletRequest, HttpServletRequest, HttpServletResponse> {
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.servlet-3.0");

  public static final Servlet3Decorator DECORATE = new Servlet3Decorator();

  @Override
  protected String method(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getMethod();
  }

  @Override
  protected URI url(final HttpServletRequest httpServletRequest) throws URISyntaxException {
    return new URI(
        httpServletRequest.getScheme(),
        null,
        httpServletRequest.getServerName(),
        httpServletRequest.getServerPort(),
        httpServletRequest.getRequestURI(),
        httpServletRequest.getQueryString(),
        null);
  }

  @Override
  protected String peerHostIP(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemoteAddr();
  }

  @Override
  protected Integer peerPort(final HttpServletRequest httpServletRequest) {
    return httpServletRequest.getRemotePort();
  }

  @Override
  protected Integer status(final HttpServletResponse httpServletResponse) {
    return httpServletResponse.getStatus();
  }

  @Override
  public Span onRequest(final Span span, final HttpServletRequest request) {
    assert span != null;
    if (request != null) {
      span.setAttribute("servlet.path", request.getServletPath());
      span.setAttribute("servlet.context", request.getContextPath());
      onContext(span, request, request.getServletContext());
    }
    return super.onRequest(span, request);
  }

  /**
   * This method executes the filter created by
   * io.opentelemetry.auto.instrumentation.springwebmvc.DispatcherServletInstrumentation$HandlerMappingAdvice.
   * This was easier and less "hacky" than other ways to add the filter to the front of the filter
   * chain.
   */
  private void onContext(
      final Span span, final HttpServletRequest request, final ServletContext context) {
    if (context == null) {
      // some frameworks (jetty) may return a null context.
      return;
    }
    final Object attribute = context.getAttribute("io.opentelemetry.auto.dispatcher-filter");
    if (attribute instanceof Filter) {
      final Object priorAttr = request.getAttribute(SPAN_ATTRIBUTE);
      request.setAttribute(SPAN_ATTRIBUTE, span);
      try {
        ((Filter) attribute).doFilter(request, null, null);
      } catch (final IOException | ServletException e) {
        log.debug("Exception unexpectedly thrown by filter", e);
      } finally {
        request.setAttribute(SPAN_ATTRIBUTE, priorAttr);
      }
    }
  }

  @Override
  public Span onError(final Span span, final Throwable throwable) {
    if (throwable instanceof ServletException && throwable.getCause() != null) {
      super.onError(span, throwable.getCause());
    } else {
      super.onError(span, throwable);
    }
    return span;
  }
}

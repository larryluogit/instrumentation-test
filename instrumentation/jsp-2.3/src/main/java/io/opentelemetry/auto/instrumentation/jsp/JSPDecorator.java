/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.jsp;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.BaseDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.HttpJspPage;
import org.apache.jasper.JspCompilationContext;
import org.slf4j.LoggerFactory;

public class JSPDecorator extends BaseDecorator {
  public static final JSPDecorator DECORATE = new JSPDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.jsp-2.3");

  @Override
  protected String getSpanType() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "jsp-http-servlet";
  }

  public void onCompile(final Span span, final JspCompilationContext jspCompilationContext) {
    if (jspCompilationContext != null) {
      span.setAttribute(MoreTags.RESOURCE_NAME, jspCompilationContext.getJspFile());

      if (jspCompilationContext.getServletContext() != null) {
        span.setAttribute(
            "servlet.context", jspCompilationContext.getServletContext().getContextPath());
      }

      if (jspCompilationContext.getCompiler() != null) {
        span.setAttribute("jsp.compiler", jspCompilationContext.getCompiler().getClass().getName());
      }
      span.setAttribute("jsp.classFQCN", jspCompilationContext.getFQCN());
    }
  }

  public void onRender(final Span span, final HttpServletRequest req) {
    // get the JSP file name being rendered in an include action
    final Object includeServletPath = req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
    String resourceName = req.getServletPath();
    if (includeServletPath instanceof String) {
      resourceName = includeServletPath.toString();
    }
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);

    final Object forwardOrigin = req.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
    if (forwardOrigin instanceof String) {
      span.setAttribute("jsp.forwardOrigin", forwardOrigin.toString());
    }

    // add the request URL as a tag to provide better context when looking at spans produced by
    // actions. Tomcat 9 has relative path symbols in the value returned from
    // HttpServletRequest#getRequestURL(),
    // normalizing the URL should remove those symbols for readability and consistency
    try {
      span.setAttribute(
          "jsp.requestURL", (new URI(req.getRequestURL().toString())).normalize().toString());
    } catch (final URISyntaxException uriSE) {
      LoggerFactory.getLogger(HttpJspPage.class)
          .warn("Failed to get and normalize request URL: " + uriSE.getMessage());
    }
  }
}

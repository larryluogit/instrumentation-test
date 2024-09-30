/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.dispatch;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.AUTH_REQUIRED;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_PARAMETERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat.RequestDispatcherServlet;
import jakarta.servlet.Servlet;
import org.apache.catalina.Context;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatServlet5ForwardTest extends TomcatDispatchTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Sync.class; // dispatch to sync servlet
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestNotFound(false);
  }

  @Override
  protected void setupServlets(Context context) throws Exception {
    super.setupServlets(context);

    addServlet(context, "/dispatch" + SUCCESS.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + QUERY_PARAM.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + REDIRECT.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + ERROR.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(context, "/dispatch" + EXCEPTION.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + AUTH_REQUIRED.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + CAPTURE_HEADERS.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context,
        "/dispatch" + CAPTURE_PARAMETERS.getPath(),
        RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + INDEXED_CHILD.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context, "/dispatch" + HTML_PRINT_WRITER.getPath(), RequestDispatcherServlet.Forward.class);
    addServlet(
        context,
        "/dispatch" + HTML_SERVLET_OUTPUT_STREAM.getPath(),
        RequestDispatcherServlet.Forward.class);
  }
}
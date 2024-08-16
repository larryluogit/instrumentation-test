/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.mapping;

import java.io.IOException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

class JettyServlet3MappingTest extends AbstractServlet3MappingTest<Server, ServletContextHandler> {

  @Override
  protected Server setupServer() {
    Server server = new Server(port);
    ServletContextHandler handler = new ServletContextHandler(null, getContextPath());
    setupServlets(handler);
    server.setHandler(handler);
    try {
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return server;
  }

  @Override
  public void stopServer(Server server) {
    try {
      server.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    server.destroy();
  }

  @Override
  protected void setupServlets(ServletContextHandler handler) {
    super.setupServlets(handler);

    addServlet(handler, "/", DefaultServlet.class);
  }

  @Override
  public void addServlet(
      ServletContextHandler servletContextHandler, String path, Class<? extends Servlet> servlet) {
    servletContextHandler.addServlet(servlet, path);
  }

  @Override
  public String getContextPath() {
    return "/jetty-context";
  }

  public static class DefaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.sendError(404);
    }
  }
}

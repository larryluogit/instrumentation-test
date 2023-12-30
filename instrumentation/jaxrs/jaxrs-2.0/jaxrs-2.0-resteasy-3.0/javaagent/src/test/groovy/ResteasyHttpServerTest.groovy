/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.api.internal.HttpConstants
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.undertow.Undertow
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer
import test.JaxRsTestApplication

class ResteasyHttpServerTest extends JaxRsHttpServerTest<UndertowJaxrsServer> {

  @Override
  String getContextPath() {
    "/resteasy-context"
  }

  @Override
  UndertowJaxrsServer startServer(int port) {
    def server = new UndertowJaxrsServer()
    server.deploy(JaxRsTestApplication, getContextPath())
    server.start(Undertow.builder()
      .addHttpListener(port, "localhost"))
    return server
  }

  @Override
  void stopServer(UndertowJaxrsServer server) {
    server.stop()
  }

  // resteasy 3.0.x does not support JAX-RS 2.1
  @Override
  boolean shouldTestCompletableStageAsync() {
    false
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (method == HttpConstants._OTHER) {
      return "${getContextPath()}/*"
    }
    return super.expectedHttpRoute(endpoint, method)
  }

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    500
  }
}

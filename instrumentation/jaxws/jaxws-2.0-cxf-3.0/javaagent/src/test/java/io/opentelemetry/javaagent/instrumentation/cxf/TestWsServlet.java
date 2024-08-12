/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import hello.HelloServiceImpl;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import javax.servlet.ServletConfig;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;

class TestWsServlet extends CXFNonSpringServlet {

  @Override
  public void loadBus(ServletConfig servletConfig) {
    super.loadBus(servletConfig);

    // publish test webservice
    HelloServiceImpl implementor = new HelloServiceImpl();
    EndpointImpl endpoint = new EndpointImpl(bus, implementor);
    endpoint.publish("/HelloService");
    endpoint
        .getOutInterceptors()
        .add(
            new AbstractPhaseInterceptor<Message>(Phase.SETUP) {
              @Override
              public void handleMessage(Message message) {
                Context context = Context.current();
                if (LocalRootSpan.fromContext(context) != Span.fromContext(context)) {
                  throw new IllegalStateException(
                      "handler span should be ended before outgoing interceptors");
                }
              }
            });
  }
}

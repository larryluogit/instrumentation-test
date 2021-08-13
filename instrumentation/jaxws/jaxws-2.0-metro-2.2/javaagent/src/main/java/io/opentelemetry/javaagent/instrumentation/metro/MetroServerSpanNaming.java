/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import com.sun.xml.ws.api.message.Packet;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import java.util.function.Supplier;
import javax.servlet.http.HttpServletRequest;
import javax.xml.ws.handler.MessageContext;

public class MetroServerSpanNaming {

  private MetroServerSpanNaming() {}

  public static Supplier<String> getServerSpanNameSupplier(Context context, MetroRequest request) {
    return () -> getServerSpanName(context, request);
  }

  private static String getServerSpanName(Context context, MetroRequest metroRequest) {
    String spanName = metroRequest.spanName();
    if (spanName == null) {
      return null;
    }

    Packet packet = metroRequest.packet();
    String serverSpanName = spanName;
    HttpServletRequest request = (HttpServletRequest) packet.get(MessageContext.SERVLET_REQUEST);
    if (request != null) {
      String servletPath = request.getServletPath();
      if (!servletPath.isEmpty()) {
        String pathInfo = request.getPathInfo();
        if (pathInfo != null) {
          serverSpanName = servletPath + "/" + spanName;
        } else {
          // when pathInfo is null then there is a servlet that is mapped to this exact service
          // servletPath already contains the service name
          String operationName = packet.getWSDLOperation().getLocalPart();
          serverSpanName = servletPath + "/" + operationName;
        }
      }
    }

    return ServletContextPath.prepend(context, serverSpanName);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import javax.ws.rs.container.ContainerRequestContext;

public final class RequestContextHelper {
  public static Context createOrUpdateAbortSpan(
      Instrumenter<HandlerData, Void> instrumenter,
      ContainerRequestContext requestContext,
      HandlerData handlerData) {

    if (handlerData == null) {
      return null;
    }

    requestContext.setProperty(JaxrsConstants.ABORT_HANDLED, true);
    Context parentContext = Java8BytecodeBridge.currentContext();
    Span serverSpan = LocalRootSpan.fromContextOrNull(parentContext);
    Span currentSpan = Java8BytecodeBridge.spanFromContext(parentContext);

    HttpRouteHolder.updateHttpRoute(
        parentContext,
        HttpRouteSource.CONTROLLER,
        JaxrsServerSpanNaming.SERVER_SPAN_NAME,
        handlerData);

    if (currentSpan != null && currentSpan != serverSpan) {
      // there's already an active span, and it's not the same as the server (servlet) span,
      // so we don't want to start a JAX-RS one
      return null;
    }

    if (!instrumenter.shouldStart(parentContext, handlerData)) {
      return null;
    }

    return instrumenter.start(parentContext, handlerData);
  }

  private RequestContextHelper() {}
}

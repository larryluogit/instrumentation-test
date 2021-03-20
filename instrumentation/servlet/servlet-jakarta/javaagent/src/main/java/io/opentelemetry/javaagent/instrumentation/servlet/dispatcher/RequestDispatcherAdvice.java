/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import static io.opentelemetry.instrumentation.api.tracer.HttpServerTracer.CONTEXT_ATTRIBUTE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.jakarta.JakartaServletAccessor;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class RequestDispatcherAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void start(
      @Advice.Origin Method method,
      @Advice.This RequestDispatcher dispatcher,
      @Advice.Local("_originalContext") Object originalContext,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Argument(0) ServletRequest request) {

    Context parent =
        RequestDispatcherAdviceHelper.getStartParentContext(
            JakartaServletAccessor.INSTANCE, (HttpServletRequest) request);

    if (parent == null) {
      return;
    }

    try (Scope ignored = parent.makeCurrent()) {
      context = RequestDispatcherTracer.tracer().startSpan(method);

      // save the original servlet span before overwriting the request attribute, so that it can
      // be
      // restored on method exit
      originalContext = request.getAttribute(CONTEXT_ATTRIBUTE);

      // this tells the dispatched servlet to use the current span as the parent for its work
      request.setAttribute(CONTEXT_ATTRIBUTE, context);
    }
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stop(
      @Advice.Local("_originalContext") Object originalContext,
      @Advice.Argument(0) ServletRequest request,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Thrown Throwable throwable) {

    RequestDispatcherAdviceHelper.stop(
        RequestDispatcherTracer.tracer(),
        JakartaServletAccessor.INSTANCE,
        originalContext,
        (HttpServletRequest) request,
        context,
        scope,
        throwable);
  }
}

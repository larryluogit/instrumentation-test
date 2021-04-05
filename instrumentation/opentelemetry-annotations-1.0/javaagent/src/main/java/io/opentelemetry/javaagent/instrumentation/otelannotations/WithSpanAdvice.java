/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import static io.opentelemetry.javaagent.instrumentation.otelannotations.WithSpanTracer.tracer;

import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * Instrumentation for methods annotated with {@link WithSpan} annotation.
 *
 * @see WithSpanInstrumentationModule
 */
public class WithSpanAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    WithSpan applicationAnnotation = method.getAnnotation(WithSpan.class);

    SpanKind kind = tracer().extractSpanKind(applicationAnnotation);
    Context current = Java8BytecodeBridge.currentContext();

    // don't create a nested span if you're not supposed to.
    if (tracer().shouldStartSpan(current, kind)) {
      context = tracer().startSpan(current, applicationAnnotation, method, kind);
      scope = context.makeCurrent();
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Origin Method method,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope,
      @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
      @Advice.Thrown Throwable throwable) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      returnValue = tracer().end(context, method.getReturnType(), returnValue);
    }
  }
}

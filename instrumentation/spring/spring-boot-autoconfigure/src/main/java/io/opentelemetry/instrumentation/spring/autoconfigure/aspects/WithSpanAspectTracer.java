/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.instrumentation.api.tracer.AttributeBindings;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategies;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;

class WithSpanAspectTracer extends BaseTracer {

  private final WithSpanAspectAttributeBinder withSpanAspectAttributeBinder;
  private final AsyncSpanEndStrategies asyncSpanEndStrategies =
      AsyncSpanEndStrategies.getInstance();

  WithSpanAspectTracer(
      OpenTelemetry openTelemetry, WithSpanAspectAttributeBinder withSpanAspectAttributeBinder) {
    super(openTelemetry);
    this.withSpanAspectAttributeBinder = withSpanAspectAttributeBinder;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.spring-boot-autoconfigure-aspect";
  }

  Context startSpan(
      Context parentContext, WithSpan annotation, Method method, JoinPoint joinPoint) {
    SpanBuilder spanBuilder =
        spanBuilder(parentContext, spanName(annotation, method), annotation.kind());
    Span span = withSpanAttributes(spanBuilder, method, joinPoint).startSpan();
    switch (annotation.kind()) {
      case SERVER:
        return withServerSpan(parentContext, span);
      case CLIENT:
        return withClientSpan(parentContext, span);
      default:
        return parentContext.with(span);
    }
  }

  private static String spanName(WithSpan annotation, Method method) {
    String spanName = annotation.value();
    if (spanName.isEmpty()) {
      return SpanNames.fromMethod(method);
    }
    return spanName;
  }

  public SpanBuilder withSpanAttributes(
      SpanBuilder spanBuilder, Method method, JoinPoint joinPoint) {
    AttributeBindings bindings = withSpanAspectAttributeBinder.bind(method);
    if (!bindings.isEmpty()) {
      bindings.apply(spanBuilder::setAttribute, joinPoint.getArgs());
    }
    return spanBuilder;
  }

  /**
   * Denotes the end of the invocation of the traced method with a successful result which will end
   * the span stored in the passed {@code context}. If the method returned a value representing an
   * asynchronous operation then the span will not be finished until the asynchronous operation has
   * completed.
   *
   * @param returnType Return type of the traced method.
   * @param returnValue Return value from the traced method.
   * @return Either {@code returnValue} or a value composing over {@code returnValue} for
   *     notification of completion.
   */
  public Object end(Context context, Class<?> returnType, Object returnValue) {
    if (returnType.isInstance(returnValue)) {
      AsyncSpanEndStrategy asyncSpanEndStrategy =
          asyncSpanEndStrategies.resolveStrategy(returnType);
      if (asyncSpanEndStrategy != null) {
        return asyncSpanEndStrategy.end(this, context, returnValue);
      }
    }
    end(context);
    return returnValue;
  }
}

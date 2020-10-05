/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spring.webflux.server;

import static io.opentelemetry.instrumentation.auto.spring.webflux.server.SpringWebfluxHttpServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.grpc.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
import net.bytebuddy.asm.Advice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

public class HandlerAdapterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static SpanWithScope methodEnter(
      @Advice.Argument(0) ServerWebExchange exchange, @Advice.Argument(1) Object handler) {

    SpanWithScope spanWithScope = null;
    Context context = exchange.getAttribute(AdviceUtils.CONTEXT_ATTRIBUTE);
    if (handler != null && context != null) {
      Span span = TracingContextUtils.getSpan(context);
      String handlerType;
      String operationName;

      if (handler instanceof HandlerMethod) {
        // Special case for requests mapped with annotations
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        operationName = TRACER.spanNameForMethod(handlerMethod.getMethod());
        handlerType = handlerMethod.getMethod().getDeclaringClass().getName();
      } else {
        operationName = AdviceUtils.parseOperationName(handler);
        handlerType = handler.getClass().getName();
      }

      span.updateName(operationName);
      span.setAttribute("handler.type", handlerType);

      spanWithScope = new SpanWithScope(span, currentContextWith(span));
    }

    if (context != null) {
      Span serverSpan = BaseTracer.CONTEXT_SERVER_SPAN_KEY.get(context);
      PathPattern bestPattern =
          exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (serverSpan != null && bestPattern != null) {
        serverSpan.updateName(bestPattern.getPatternString());
      }
    }

    return spanWithScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Enter SpanWithScope spanWithScope,
      @Advice.Thrown Throwable throwable) {
    if (throwable != null) {
      AdviceUtils.finishSpanIfPresent(exchange, throwable);
    }
    if (spanWithScope != null) {
      spanWithScope.closeScope();
      // span finished in SpanFinishingSubscriber
    }
  }
}

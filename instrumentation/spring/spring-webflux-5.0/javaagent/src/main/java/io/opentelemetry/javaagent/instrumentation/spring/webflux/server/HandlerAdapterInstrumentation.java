/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.webflux.server.WebfluxSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.instrumentation.api.tracer.SpanNames;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.spring.webflux.SpringWebfluxConfig;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Mono;

public class HandlerAdapterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.web.reactive.HandlerAdapter");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(implementsInterface(named("org.springframework.web.reactive.HandlerAdapter")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArgument(1, Object.class))
            .and(takesArguments(2)),
        this.getClass().getName() + "$HandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Argument(1) Object handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = exchange.getAttribute(AdviceUtils.CONTEXT_ATTRIBUTE);

      if (parentContext == null) {
        return;
      }

      Span serverSpan = ServerSpan.fromContextOrNull(parentContext);

      PathPattern bestPattern =
          exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      if (serverSpan != null && bestPattern != null) {
        serverSpan.updateName(
            ServletContextPath.prepend(Context.current(), bestPattern.toString()));
      }

      if (handler == null) {
        return;
      }

      if (!instrumenter().shouldStart(parentContext, null)) {
        return;
      }

      context = instrumenter().start(parentContext, null);

      Span span = Span.fromContext(context);
      String handlerType;
      String spanName;

      if (handler instanceof HandlerMethod) {
        // Special case for requests mapped with annotations
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        spanName = SpanNames.fromMethod(handlerMethod.getMethod());
        handlerType = handlerMethod.getMethod().getDeclaringClass().getName();
      } else {
        spanName = AdviceUtils.spanNameForHandler(handler);
        handlerType = handler.getClass().getName();
      }

      span.updateName(spanName);
      if (SpringWebfluxConfig.captureExperimentalSpanAttributes()) {
        span.setAttribute("spring-webflux.handler.type", handlerType);
      }

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Return(readOnly = false) Mono<Void> mono,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (mono != null) {
        mono = AdviceUtils.setPublisherSpan(mono, context);
        // span finished in SpanFinishingSubscriber
      } else {
        instrumenter().end(context, null, null, throwable);
      }
    }
  }
}

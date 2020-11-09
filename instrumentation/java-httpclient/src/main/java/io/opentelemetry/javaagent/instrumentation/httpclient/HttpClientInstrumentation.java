/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.httpclient.JdkHttpClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class HttpClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("java.net.http.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("java.net.")
        .or(nameStartsWith("jdk.internal."))
        .and(not(named("jdk.internal.net.http.HttpClientFacade")))
        .and(extendsClass(named("java.net.http.HttpClient")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod()
            .and(named("send"))
            .and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        HttpClientInstrumentation.class.getName() + "$SendAdvice");

    transformers.put(
        isMethod()
            .and(named("sendAsync"))
            .and(isPublic())
            .and(takesArgument(0, named("java.net.http.HttpRequest"))),
        HttpClientInstrumentation.class.getName() + "$SendAsyncAdvice");

    return transformers;
  }

  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {

      callDepth = tracer().getCallDepth();
      if (callDepth.getAndIncrement() == 0) {
        span = tracer().startSpan(httpRequest);
        if (span.getSpanContext().isValid()) {
          scope = tracer().startScope(span, httpRequest);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return HttpResponse<?> result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {

      if (callDepth.decrementAndGet() == 0 && scope != null) {
        scope.close();
        if (throwable == null) {
          tracer().end(span, result);
        } else {
          tracer().endExceptionally(span, result, throwable);
        }
      }
    }
  }

  public static class SendAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {

      callDepth = tracer().getCallDepth();
      if (callDepth.getAndIncrement() == 0) {
        span = tracer().startSpan(httpRequest);
        if (span.getSpanContext().isValid()) {
          scope = tracer().startScope(span, httpRequest);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return(readOnly = false) CompletableFuture<HttpResponse<?>> future,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelCallDepth") Depth callDepth) {

      if (callDepth.decrementAndGet() == 0 && scope != null) {
        scope.close();
        if (throwable != null) {
          tracer().endExceptionally(span, null, throwable);
        } else {
          future = future.whenComplete(new ResponseConsumer(span));
        }
      }
    }
  }
}

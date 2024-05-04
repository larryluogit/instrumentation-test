/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.KotlinCoroutineUtil.isKotlinSuspendMethod;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasParameters;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.whereAny;

import com.google.common.base.Stopwatch;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class TimedInstrumentation implements TypeInstrumentation {

  private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;
  private final ElementMatcher.Junction<MethodDescription> annotatedParametersMatcher;
  // this matcher matches all methods that should be excluded from transformation
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  TimedInstrumentation() {
    annotatedMethodMatcher =
        isAnnotatedWith(named("application.io.opentelemetry.instrumentation.annotations.Timed"));
    annotatedParametersMatcher =
        hasParameters(
            whereAny(
                isAnnotatedWith(
                    named(
                        "application.io.opentelemetry.instrumentation.annotations.MetricAttribute"))));
    // exclude all kotlin suspend methods, these are handle in kotlinx-coroutines instrumentation
    excludedMethodsMatcher =
        AnnotationExcludedMethods.configureExcludedMethods().or(isKotlinSuspendMethod());
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(annotatedMethodMatcher);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> timedMethods =
        annotatedMethodMatcher.and(not(excludedMethodsMatcher));

    ElementMatcher.Junction<MethodDescription> timedMethodsWithParameters =
        timedMethods.and(annotatedParametersMatcher);

    ElementMatcher.Junction<MethodDescription> timedMethodsWithoutParameters =
        timedMethods.and(not(annotatedParametersMatcher));

    transformer.applyAdviceToMethod(
        timedMethodsWithoutParameters, TimedInstrumentation.class.getName() + "$TimedAdvice");

    // Only apply advice for tracing parameters as attributes if any of the parameters are annotated
    // with @MetricsAttribute to avoid unnecessarily copying the arguments into an array.
    transformer.applyAdviceToMethod(
        timedMethodsWithParameters,
        TimedInstrumentation.class.getName() + "$TimedAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class TimedAttributesAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method originMethod,
        @Advice.Local("otelMethod") Method method,
        @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] args,
        @Advice.Local("otelRequest") MethodRequest request,
        @Advice.Local("stopwatch") Stopwatch stopwatch) {

      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to local variable so that there would be only one call to Class.getMethod.
      method = originMethod;
      request = new MethodRequest(method, args);
      stopwatch = Stopwatch.createStarted();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("otelRequest") MethodRequest request,
        @Advice.Local("stopwatch") Stopwatch stopwatch,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      AnnotationSingletons.recordHistogramWithAttributes(request, stopwatch);
    }
  }

  @SuppressWarnings("unused")
  public static class TimedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method originMethod,
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("stopwatch") Stopwatch stopwatch) {
      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to local variable so that there would be only one call to Class.getMethod.
      method = originMethod;
      stopwatch = Stopwatch.createStarted();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("stopwatch") Stopwatch stopwatch,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      AnnotationSingletons.recordHistogram(method, stopwatch);
    }
  }
}

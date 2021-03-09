/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static io.opentelemetry.javaagent.instrumentation.grails.GrailsTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefaultGrailsControllerClassInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.grails.core.DefaultGrailsControllerClass");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("invoke"))
            .and(takesArgument(0, named(Object.class.getName())))
            .and(takesArgument(1, named(String.class.getName())))
            .and(takesArguments(2)),
        DefaultGrailsControllerClassInstrumentation.class.getName() + "$ControllerAdvice");
  }

  public static class ControllerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startSpan(
        @Advice.Argument(0) Object controller,
        @Advice.Argument(1) String action,
        @Advice.FieldValue("defaultActionName") String defaultActionName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      context = tracer().startSpan(controller, action != null ? action : defaultActionName);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      if (throwable == null) {
        tracer().end(context);
      } else {
        tracer().endExceptionally(context, throwable);
      }
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.methods.MethodTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodInstrumentation implements TypeInstrumentation {
  private final String className;
  private final Set<String> methodNames;

  public MethodInstrumentation(String className, Set<String> methodNames) {
    this.className = className;
    this.methodNames = methodNames;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(className);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named(className));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> methodMatchers = null;
    for (String methodName : methodNames) {
      if (methodMatchers == null) {
        methodMatchers = named(methodName);
      } else {
        methodMatchers = methodMatchers.or(named(methodName));
      }
    }

    transformer.applyAdviceToMethod(
        methodMatchers, MethodInstrumentation.class.getName() + "$MethodAdvice");
  }

  public static class MethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = tracer().startSpan(method);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }
}

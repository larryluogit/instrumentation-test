/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.appender.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.appender.LogEmitterProvider;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

class Log4jAppenderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.logging.log4j.spi.AbstractLogger"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.logging.log4j.spi.AbstractLogger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("logMessage"))
            .and(takesArguments(5))
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(2, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(3, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(4, named("java.lang.Throwable"))),
        Log4jAppenderInstrumentation.class.getName() + "$LogMessageAdvice");
    // log4j 2.12.1 introduced and started using this new log() method
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected().or(isPublic()))
            .and(named("log"))
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.apache.logging.log4j.Level")))
            .and(takesArgument(1, named("org.apache.logging.log4j.Marker")))
            .and(takesArgument(2, named("java.lang.String")))
            .and(takesArgument(3, named("java.lang.StackTraceElement")))
            .and(takesArgument(4, named("org.apache.logging.log4j.message.Message")))
            .and(takesArgument(5, named("java.lang.Throwable"))),
        Log4jAppenderInstrumentation.class.getName() + "$LogAdvice");
  }

  @SuppressWarnings("unused")
  public static class LogMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(1) final Level level,
        @Advice.Argument(3) final Message message,
        @Advice.Argument(4) final Throwable t,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        Log4jHelper.capture(logger, level, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }

  @SuppressWarnings("unused")
  public static class LogAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This final Logger logger,
        @Advice.Argument(0) final Level level,
        @Advice.Argument(4) final Message message,
        @Advice.Argument(5) final Throwable t,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // need to track call depth across all loggers in order to avoid double capture when one
      // logging framework delegates to another
      callDepth = CallDepth.forClass(LogEmitterProvider.class);
      if (callDepth.getAndIncrement() == 0) {
        Log4jHelper.capture(logger, level, message, t);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth.decrementAndGet();
    }
  }
}

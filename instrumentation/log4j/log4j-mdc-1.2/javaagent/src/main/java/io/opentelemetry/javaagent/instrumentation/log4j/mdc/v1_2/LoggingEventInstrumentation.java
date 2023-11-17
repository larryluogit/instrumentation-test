/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ConfiguredResourceAttributesHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.log4j.spi.LoggingEvent;

public class LoggingEventInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.log4j.spi.LoggingEvent");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("getMDC"))
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        LoggingEventInstrumentation.class.getName() + "$GetMdcAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This LoggingEvent event,
        @Advice.Argument(0) String key,
        @Advice.Return(readOnly = false) Object value) {
      if (CommonConfig.get().getLoggingKeysTraceId().equals(key) || CommonConfig.get()
          .getLoggingKeysSpanId().equals(key) || CommonConfig.get().getLoggingKeysTraceFlags()
          .equals(key)) {
        if (value != null) {
          // Assume already instrumented event if traceId/spanId/sampled is present.
          return;
        }

        Context context = VirtualField.find(LoggingEvent.class, Context.class).get(event);
        if (context == null) {
          return;
        }

        SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();
        if (!spanContext.isValid()) {
          return;
        }

        if (CommonConfig.get().getLoggingKeysTraceId().equals(key)) {
          value = spanContext.getTraceId();
        }
        if (CommonConfig.get().getLoggingKeysSpanId().equals(key)) {
          value = spanContext.getSpanId();
        }
        if (CommonConfig.get().getLoggingKeysTraceFlags().equals(key)) {
          value = spanContext.getTraceFlags().asHex();
        }
      } else if (value == null) {
        value = ConfiguredResourceAttributesHolder.getAttributeValue(key);
      }
    }
  }
}

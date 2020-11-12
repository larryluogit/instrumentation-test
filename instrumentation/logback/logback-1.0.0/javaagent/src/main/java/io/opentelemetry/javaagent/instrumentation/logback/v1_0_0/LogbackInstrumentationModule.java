/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.v1_0_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class LogbackInstrumentationModule extends InstrumentationModule {
  public LogbackInstrumentationModule() {
    super("logback");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.logback.v1_0_0.internal.UnionMap",
      "io.opentelemetry.instrumentation.logback.v1_0_0.internal.UnionMap$ConcatenatedSet",
      "io.opentelemetry.instrumentation.logback.v1_0_0.internal.UnionMap$ConcatenatedSet$ConcatenatedSetIterator"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new LoggerInstrumentation(), new LoggingEventInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("ch.qos.logback.classic.spi.ILoggingEvent", Span.class.getName());
  }
}

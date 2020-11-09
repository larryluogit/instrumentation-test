/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.amqp;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RabbitMqInstrumentationModule extends InstrumentationModule {
  public RabbitMqInstrumentationModule() {
    super("amqp", "rabbitmq");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RabbitTracer",
      packageName + ".TextMapInjectAdapter",
      packageName + ".TextMapExtractAdapter",
      packageName + ".TracedDelegatingConsumer",
      RabbitCommandInstrumentation.class.getName() + "$SpanHolder",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RabbitChannelInstrumentation(), new RabbitCommandInstrumentation());
  }
}

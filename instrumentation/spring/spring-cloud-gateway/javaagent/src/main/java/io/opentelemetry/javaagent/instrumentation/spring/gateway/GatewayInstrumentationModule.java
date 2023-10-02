/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class GatewayInstrumentationModule extends InstrumentationModule {

  public GatewayInstrumentationModule() {
    super("spring-cloud-gateway");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new HandlerAdapterInstrumentation());
  }

  @Override
  public int order() {
    // Later than Spring Webflux.
    return 1;
  }
}

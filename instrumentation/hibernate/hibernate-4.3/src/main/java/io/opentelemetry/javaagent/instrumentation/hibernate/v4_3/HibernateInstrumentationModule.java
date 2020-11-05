/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_3;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class HibernateInstrumentationModule extends InstrumentationModule {
  public HibernateInstrumentationModule() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils",
      "io.opentelemetry.javaagent.instrumentation.hibernate.HibernateDecorator",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ProcedureCallInstrumentation(), new SessionInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", Context.class.getName());
    map.put("org.hibernate.procedure.ProcedureCall", Context.class.getName());
    return Collections.unmodifiableMap(map);
  }
}

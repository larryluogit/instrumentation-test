/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v1_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JaxRsInstrumentationModule extends InstrumentationModule {
  public JaxRsInstrumentationModule() {
    super("jax-rs", "jaxrs");
  }

  // this is required to make sure instrumentation won't apply to jax-rs 2
  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("javax.ws.rs.container.AsyncResponse"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable",
      "io.opentelemetry.javaagent.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsTracer",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new JaxRsAnnotationsInstrumentation());
  }
}

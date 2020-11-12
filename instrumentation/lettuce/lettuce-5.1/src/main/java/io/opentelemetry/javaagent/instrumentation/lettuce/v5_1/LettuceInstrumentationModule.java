/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_1;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.lettuce.core.resource.DefaultClientResources;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class LettuceInstrumentationModule extends InstrumentationModule {

  public LettuceInstrumentationModule() {
    super("lettuce", "lettuce-5", "lettuce-5.1");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.lettuce.LettuceArgSplitter",
      packageName + ".OpenTelemetryTracing",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTracerProvider",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTraceContextProvider",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTraceContext",
      packageName + ".OpenTelemetryTracing$OpenTelemetryEndpoint",
      packageName + ".OpenTelemetryTracing$OpenTelemetryTracer",
      packageName + ".OpenTelemetryTracing$OpenTelemetrySpan",
    };
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("io.lettuce.core.tracing.Tracing");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DefaultClientResourcesInstrumentation());
  }

  private static final class DefaultClientResourcesInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("io.lettuce.core.resource.DefaultClientResources");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(isStatic()).and(named("builder")),
          LettuceInstrumentationModule.class.getName() + "$DefaultClientResourcesAdvice");
    }
  }

  public static class DefaultClientResourcesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodEnter(@Advice.Return DefaultClientResources.Builder builder) {
      builder.tracing(OpenTelemetryTracing.INSTANCE);
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class OshiInstrumentationModule extends InstrumentationModule {

  public OshiInstrumentationModule() {
    super("oshi");
  }

  @Override
  public final String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.oshi.SystemMetrics",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$1",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$2",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$3",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$4",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$5",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$6",
      "io.opentelemetry.instrumentation.oshi.SystemMetrics$7"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SystemInfoInstrumentation());
  }

  private static final class SystemInfoInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      return hasClassesNamed("oshi.SystemInfo");
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("oshi.SystemInfo");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          isMethod().and(isPublic()).and(isStatic()).and(named("getCurrentPlatformEnum")),
          OshiInstrumentationModule.class.getName() + "$OshiInstrumentationAdvice");
    }
  }

  public static class OshiInstrumentationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      SystemMetrics.registerObservers();
    }
  }
}

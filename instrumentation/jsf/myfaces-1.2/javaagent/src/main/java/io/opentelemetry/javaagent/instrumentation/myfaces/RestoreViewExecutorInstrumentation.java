/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import static io.opentelemetry.javaagent.instrumentation.myfaces.MyFacesTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.Map;
import javax.faces.context.FacesContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RestoreViewExecutorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.myfaces.lifecycle.RestoreViewExecutor");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("execute").and(takesArgument(0, named("javax.faces.context.FacesContext"))),
        RestoreViewExecutorInstrumentation.class.getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) FacesContext facesContext) {
      tracer().updateServerSpanName(Java8BytecodeBridge.currentContext(), facesContext);
    }
  }
}

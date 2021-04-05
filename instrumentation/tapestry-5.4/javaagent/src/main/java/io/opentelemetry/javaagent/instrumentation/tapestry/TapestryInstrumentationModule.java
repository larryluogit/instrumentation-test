/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import static io.opentelemetry.javaagent.instrumentation.tapestry.TapestryTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.tapestry5.internal.structure.ComponentPageElementImpl;
import org.apache.tapestry5.services.ComponentEventRequestParameters;
import org.apache.tapestry5.services.PageRenderRequestParameters;

@AutoService(InstrumentationModule.class)
public class TapestryInstrumentationModule extends InstrumentationModule {

  public TapestryInstrumentationModule() {
    super("tapestry", "tapestry-5.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in tapestry 5.4.0
    return hasClassesNamed("org.apache.tapestry5.Binding2");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new InitializeActivePageNameInstrumentation(),
        new ComponentPageElementImplInstrumentation());
  }

  public static class InitializeActivePageNameInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("org.apache.tapestry5.services.InitializeActivePageName");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();

      transformers.put(
          isMethod()
              .and(isPublic())
              .and(named("handleComponentEvent"))
              .and(takesArguments(2))
              .and(
                  takesArgument(
                      0, named("org.apache.tapestry5.services.ComponentEventRequestParameters")))
              .and(
                  takesArgument(1, named("org.apache.tapestry5.services.ComponentRequestHandler"))),
          InitializeActivePageNameInstrumentation.class.getName() + "$ComponentEventAdvice");
      transformers.put(
          isMethod()
              .and(isPublic())
              .and(named("handlePageRender"))
              .and(takesArguments(2))
              .and(
                  takesArgument(
                      0, named("org.apache.tapestry5.services.PageRenderRequestParameters")))
              .and(
                  takesArgument(1, named("org.apache.tapestry5.services.ComponentRequestHandler"))),
          InitializeActivePageNameInstrumentation.class.getName() + "$PageRenderAdvice");

      return transformers;
    }

    public static class ComponentEventAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(@Advice.Argument(0) ComponentEventRequestParameters parameters) {
        tracer().updateServerSpanName(parameters.getActivePageName());
      }
    }

    public static class PageRenderAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(@Advice.Argument(0) PageRenderRequestParameters parameters) {
        tracer().updateServerSpanName(parameters.getLogicalPageName());
      }
    }
  }

  public static class ComponentPageElementImplInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("org.apache.tapestry5.internal.structure.ComponentPageElementImpl");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return Collections.singletonMap(
          isMethod()
              .and(named("processEventTriggering"))
              .and(takesArguments(3))
              .and(takesArgument(0, String.class))
              .and(takesArgument(1, named("org.apache.tapestry5.EventContext")))
              .and(takesArgument(2, named("org.apache.tapestry5.ComponentEventCallback"))),
          ComponentPageElementImplInstrumentation.class.getName() + "$EventAdvice");
    }

    public static class EventAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.This ComponentPageElementImpl componentPageElementImpl,
          @Advice.Argument(0) String eventType,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        context = tracer().startEventSpan(eventType, componentPageElementImpl.getCompleteId());
        scope = context.makeCurrent();
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        scope.close();

        if (throwable != null) {
          tracer().endExceptionally(context, throwable);
        } else {
          tracer().end(context);
        }
      }
    }
  }
}

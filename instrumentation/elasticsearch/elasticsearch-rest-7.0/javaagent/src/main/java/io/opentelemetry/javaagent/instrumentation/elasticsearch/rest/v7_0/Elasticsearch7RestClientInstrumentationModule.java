/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.v7_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.ElasticsearchRestClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.RestResponseListener;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

@AutoService(InstrumentationModule.class)
public class Elasticsearch7RestClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch7RestClientInstrumentationModule() {
    super("elasticsearch-rest", "elasticsearch-rest-7.0", "elasticsearch");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class introduced in 7.0.0
    return hasClassesNamed("org.elasticsearch.client.RestClient$InternalRequest");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RestClientInstrumentation());
  }

  public static class RestClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.elasticsearch.client.RestClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isMethod()
              .and(named("performRequest"))
              .and(takesArguments(1))
              .and(takesArgument(0, named("org.elasticsearch.client.Request"))),
          Elasticsearch7RestClientInstrumentationModule.class.getName() + "$PerformRequestAdvice");
      transformers.put(
          isMethod()
              .and(named("performRequestAsync"))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.elasticsearch.client.Request")))
              .and(takesArgument(1, named("org.elasticsearch.client.ResponseListener"))),
          Elasticsearch7RestClientInstrumentationModule.class.getName()
              + "$PerformRequestAsyncAdvice");
      return transformers;
    }
  }

  public static class PerformRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Request request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      context =
          tracer()
              .startSpan(currentContext(), null, request.getMethod() + " " + request.getEndpoint());
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Return(readOnly = false) Response response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        if (response != null) {
          tracer().onResponse(context, response);
        }
        tracer().end(context);
      }
    }
  }

  public static class PerformRequestAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(value = 1, readOnly = false) ResponseListener responseListener,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      context =
          tracer()
              .startSpan(currentContext(), null, request.getMethod() + " " + request.getEndpoint());
      scope = context.makeCurrent();

      responseListener = new RestResponseListener(responseListener, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
      // span ended in RestResponseListener
    }
  }
}

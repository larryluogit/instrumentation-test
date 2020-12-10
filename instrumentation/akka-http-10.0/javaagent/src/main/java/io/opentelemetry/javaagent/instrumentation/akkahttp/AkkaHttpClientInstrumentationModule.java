/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp;

import static io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.javadsl.model.headers.RawHeader;
import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.tracer.Operation;
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
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

@AutoService(InstrumentationModule.class)
public class AkkaHttpClientInstrumentationModule extends InstrumentationModule {
  public AkkaHttpClientInstrumentationModule() {
    super("akka-http", "akka-http-client");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new HttpExtInstrumentation());
  }

  public static class HttpExtInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("akka.http.scaladsl.HttpExt");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      // This is mainly for compatibility with 10.0
      transformers.put(
          named("singleRequest")
              .and(takesArgument(0, named("akka.http.scaladsl.model.HttpRequest"))),
          AkkaHttpClientInstrumentationModule.class.getName() + "$SingleRequestAdvice");
      // This is for 10.1+
      transformers.put(
          named("singleRequestImpl")
              .and(takesArgument(0, named("akka.http.scaladsl.model.HttpRequest"))),
          AkkaHttpClientInstrumentationModule.class.getName() + "$SingleRequestAdvice");
      return transformers;
    }
  }

  public static class SingleRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpRequest request,
        @Advice.Local("otelOperation") Operation operation,
        @Advice.Local("otelScope") Scope scope) {
      /*
      Versions 10.0 and 10.1 have slightly different structure that is hard to distinguish so here
      we cast 'wider net' and avoid instrumenting twice.
      In the future we may want to separate these, but since lots of code is reused we would need to come up
      with way of continuing to reusing it.
       */

      // Request is immutable, so we have to assign new value once we update headers
      AkkaHttpHeaders headers = new AkkaHttpHeaders(request);
      operation = tracer().startOperation(request, headers);
      scope = operation.makeCurrent();
      request = headers.getRequest();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) HttpRequest request,
        @Advice.This HttpExt thiz,
        @Advice.Return Future<HttpResponse> responseFuture,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") Operation operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable == null) {
        responseFuture.onComplete(new OnCompleteHandler(operation), thiz.system().dispatcher());
      } else {
        tracer().endExceptionally(operation, throwable);
      }
    }
  }

  public static class OnCompleteHandler extends AbstractFunction1<Try<HttpResponse>, Void> {
    private final Operation operation;

    public OnCompleteHandler(Operation operation) {
      this.operation = operation;
    }

    @Override
    public Void apply(Try<HttpResponse> result) {
      if (result.isSuccess()) {
        tracer().end(operation, result.get());
      } else {
        tracer().endExceptionally(operation, result.failed().get());
      }
      return null;
    }
  }

  public static class InjectAdapter implements TextMapPropagator.Setter<AkkaHttpHeaders> {

    public static final InjectAdapter SETTER = new InjectAdapter();

    @Override
    public void set(AkkaHttpHeaders carrier, String key, String value) {
      HttpRequest request = carrier.getRequest();
      if (request != null) {
        // It looks like this cast is only needed in Java, Scala would have figured it out
        carrier.setRequest((HttpRequest) request.addHeader(RawHeader.create(key, value)));
      }
    }
  }
}

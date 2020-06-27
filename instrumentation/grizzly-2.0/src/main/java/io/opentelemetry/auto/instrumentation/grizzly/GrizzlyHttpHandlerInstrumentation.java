/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer.CONTEXT_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.grizzly.GrizzlyHttpServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.http.server.AfterServiceListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

@AutoService(Instrumenter.class)
public class GrizzlyHttpHandlerInstrumentation extends Instrumenter.Default {

  public GrizzlyHttpHandlerInstrumentation() {
    super("grizzly");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.server.HttpHandler");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrizzlyHttpServerTracer",
      packageName + ".GrizzlyRequestExtractAdapter",
      getClass().getName() + "$SpanClosingListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("doHandle"))
            .and(takesArgument(0, named("org.glassfish.grizzly.http.server.Request")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.server.Response"))),
        GrizzlyHttpHandlerInstrumentation.class.getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Origin final Method method,
        @Advice.Argument(0) final Request request,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (TRACER.getAttachedContext(request) != null) {
        return;
      }

      request.addAfterServiceListener(SpanClosingListener.LISTENER);

      span = TRACER.startSpan(request, method, null);
      scope = TRACER.startScope(span, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(1) final Response response,
        @Advice.Thrown final Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable, response.getStatus());
      }
    }
  }

  public static class SpanClosingListener implements AfterServiceListener {
    public static final SpanClosingListener LISTENER = new SpanClosingListener();

    @Override
    public void onAfterService(final Request request) {
      final Object contextAttribute = request.getAttribute(CONTEXT_ATTRIBUTE);
      if (contextAttribute instanceof Context) {
        request.removeAttribute(CONTEXT_ATTRIBUTE);
        TRACER.end(getSpan((Context) contextAttribute), request.getResponse().getStatus());
      }
    }
  }
}

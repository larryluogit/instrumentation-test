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

package io.opentelemetry.instrumentation.auto.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrs.v2_0.JaxRsAnnotationsTracer.TRACER;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;

/**
 * RESTEasy specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the RESTEasy implementation, <code>ContainerRequestContext</code> is implemented by <code>
 * PostMatchContainerRequestContext</code>. This class provides a way to get the matched resource
 * method through <code>getResourceMethod()</code>.
 */
@AutoService(Instrumenter.class)
public class Resteasy31RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext context,
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope) {
      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && context instanceof PostMatchContainerRequestContext) {

        ResourceMethodInvoker resourceMethodInvoker =
            ((PostMatchContainerRequestContext) context).getResourceMethod();
        Method method = resourceMethodInvoker.getMethod();
        Class<?> resourceClass = resourceMethodInvoker.getResourceClass();

        span = RequestFilterHelper.createOrUpdateAbortSpan(context, resourceClass, method);
        if (span != null) {
          scope = TRACER.startScope(span);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      RequestFilterHelper.closeSpanAndScope(span, scope, throwable);
    }
  }
}

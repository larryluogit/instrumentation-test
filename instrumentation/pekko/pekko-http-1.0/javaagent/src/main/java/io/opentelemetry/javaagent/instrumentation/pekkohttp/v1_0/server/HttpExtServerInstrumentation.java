/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.stream.scaladsl.Flow;

public class HttpExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandle")
            .and(takesArgument(0, named("org.apache.pekko.stream.scaladsl.Flow"))),
        this.getClass().getName() + "$PekkoBindAndHandleAdvice");
  }

  @SuppressWarnings("unused")
  public static class PekkoBindAndHandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, ?> handler) {
      handler = PekkoFlowWrapper.wrap(handler);
    }
  }
}

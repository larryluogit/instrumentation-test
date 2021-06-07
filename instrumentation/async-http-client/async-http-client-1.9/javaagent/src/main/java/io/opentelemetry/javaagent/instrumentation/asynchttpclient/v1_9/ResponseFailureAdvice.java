/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import net.bytebuddy.asm.Advice;

public class ResponseFailureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This AsyncCompletionHandler<?> handler, @Advice.Argument(0) Throwable throwable) {

    ContextStore<AsyncHandler, Pair> contextStore =
        InstrumentationContext.get(AsyncHandler.class, Pair.class);
    Pair<Context, Context> parentAndChildContext = contextStore.get(handler);
    if (parentAndChildContext == null) {
      return null;
    }
    contextStore.put(handler, null);
    AsyncHttpClientTracer.tracer().endExceptionally(parentAndChildContext.getRight(), throwable);
    return parentAndChildContext.getLeft().makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter Scope scope) {
    if (null != scope) {
      scope.close();
    }
  }
}

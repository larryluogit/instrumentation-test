/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.kubernetesclient;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.kubernetesclient.KubernetesClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {

    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(chain.request());

    Span span = TRACER.startSpan(digest);
    TRACER.onRequest(span, chain.request());

    Context context = withSpan(span, Context.current());

    Response response;
    try (Scope scope = withScopedContext(context)) {
      response = chain.proceed(chain.request());
    } catch (Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }

    TRACER.end(span, response);
    return response;
  }
}

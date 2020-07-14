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

package io.opentelemetry.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientHelper {
  public static SpanWithScope doMethodEnter(
      final HttpUriRequest request,
      final Tracer tracer,
      final ApacheHttpClientDecorator decorator) {
    final Span span = decorator.getOrCreateSpan(request, tracer);

    decorator.afterStart(span);
    decorator.onRequest(span, request);

    final Context context = ClientDecorator.currentContextWith(span);
    if (span.getContext().isValid()) {
      decorator.inject(context, request);
    }
    final Scope scope = withScopedContext(context);

    return new SpanWithScope(span, scope);
  }

  public static void doResetCallDepthThread(final SpanWithScope spanWithScope) {
    if (spanWithScope == null) {
      return;
    }
    CallDepthThreadLocalMap.reset(HttpClient.class);
  }

  public static void doMethodExit(
      final SpanWithScope spanWithScope,
      final Object result,
      final Throwable throwable,
      final ApacheHttpClientDecorator decorator) {
    if (spanWithScope == null) {
      return;
    }

    try {
      final Span span = spanWithScope.getSpan();

      if (result instanceof HttpResponse) {
        decorator.onResponse(span, (HttpResponse) result);
      } // else they probably provided a ResponseHandler.

      decorator.onError(span, throwable);
      decorator.beforeFinish(span);
      span.end();
    } finally {
      spanWithScope.closeScope();
    }
  }
}

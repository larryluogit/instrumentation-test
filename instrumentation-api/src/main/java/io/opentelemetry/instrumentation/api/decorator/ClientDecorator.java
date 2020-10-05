/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.decorator;

import io.grpc.Context;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;

@Deprecated
public abstract class ClientDecorator extends BaseDecorator {

  // Keeps track of the client span in a subtree corresponding to a client request.
  // Visible for testing
  static final Context.Key<Span> CONTEXT_CLIENT_SPAN_KEY =
      Context.key("opentelemetry-trace-auto-client-span-key");

  /**
   * Returns a new {@link Context} forked from the {@linkplain Context#current()} current context}
   * with the {@link Span} set.
   */
  public static Context currentContextWith(Span clientSpan) {
    Context context = Context.current();
    if (clientSpan.getContext().isValid()) {
      context = context.withValue(CONTEXT_CLIENT_SPAN_KEY, clientSpan);
    }
    return TracingContextUtils.withSpan(clientSpan, context);
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * Context}, or an invalid {@link Span} otherwise.
   */
  public static Span getOrCreateSpan(String name, Tracer tracer) {
    Context context = Context.current();
    Span clientSpan = CONTEXT_CLIENT_SPAN_KEY.get(context);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    return tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(context).startSpan();
  }

  @Override
  public Span afterStart(Span span) {
    assert span != null;
    return super.afterStart(span);
  }
}

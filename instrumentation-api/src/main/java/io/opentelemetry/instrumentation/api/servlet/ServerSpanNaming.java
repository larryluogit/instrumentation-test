/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.util.function.Supplier;

/** Helper container for tracking whether instrumentation should update server span name or not. */
public final class ServerSpanNaming {

  private static final ContextKey<ServerSpanNaming> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-span-naming-key");

  public static Context init(Context context, Source initialSource) {
    if (context.get(CONTEXT_KEY) != null) {
      return context;
    }
    return context.with(CONTEXT_KEY, new ServerSpanNaming(initialSource));
  }

  private volatile Source updatedBySource;

  private ServerSpanNaming(Source initialSource) {
    this.updatedBySource = initialSource;
  }

  /**
   * If there is a server span in the context, and {@link #init(Context, Source)} has been called to
   * populate a {@code ServerSpanName} into the context, then this method will update the server
   * span name using the provided {@link Supplier} if and only if the last {@link Source} to update
   * the span name using this method has strictly lower priority than the provided {@link Source}.
   *
   * <p>If there is a server span in the context, and {@link #init(Context, Source)} has NOT been
   * called to populate a {@code ServerSpanName} into the context, then this method will update the
   * server span name using the provided {@link Supplier}.
   */
  public static void updateServerSpanName(
      Context context, Source source, Supplier<String> serverSpanName) {
    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }
    ServerSpanNaming serverSpanNaming = context.get(CONTEXT_KEY);
    if (serverSpanNaming == null) {
      serverSpan.updateName(serverSpanName.get());
      return;
    }
    if (source.order > serverSpanNaming.updatedBySource.order) {
      serverSpan.updateName(serverSpanName.get());
      serverSpanNaming.updatedBySource = source;
    }
  }

  public enum Source {
    CONTAINER(1),
    SERVLET(2),
    CONTROLLER(3);

    private final int order;

    Source(int order) {
      this.order = order;
    }
  }
}

/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.netty40.server;

import static io.opentelemetry.auto.instrumentation.netty40.server.NettyHttpServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.netty40.server.NettyHttpServerDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.netty40.server.NettyRequestExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.SERVER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.auto.instrumentation.netty40.AttributeKeys;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;

public class HttpServerRequestTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

    if (!(msg instanceof HttpRequest)) {
      final Span span = ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
      if (span == null) {
        ctx.fireChannelRead(msg); // superclass does not throw
      } else {
        try (final Scope scope = TRACER.withSpan(span)) {
          ctx.fireChannelRead(msg); // superclass does not throw
        }
      }
      return;
    }

    final HttpRequest request = (HttpRequest) msg;

    final Span.Builder spanBuilder =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(SERVER);
    try {
      final SpanContext extractedContext =
          TRACER.getHttpTextFormat().extract(request.headers(), GETTER);
      spanBuilder.setParent(extractedContext);
    } catch (final IllegalArgumentException e) {
      // Couldn't extract a context. We should treat this as a root span.
      spanBuilder.setNoParent();
    }
    final Span span = spanBuilder.startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, ctx.channel());
      DECORATE.onRequest(span, request);

      ctx.channel().attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).set(span);

      try {
        ctx.fireChannelRead(msg);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end(); // Finish the span manually since finishSpanOnClose was false
        throw throwable;
      }
    }
  }
}

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
package io.opentelemetry.auto.instrumentation.netty41.client;

import static io.opentelemetry.auto.instrumentation.netty41.client.NettyHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.netty41.client.NettyHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.netty41.client.NettyResponseInjectAdapter.SETTER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.auto.instrumentation.netty41.AttributeKeys;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    Scope parentScope = null;
    final Span parentSpan =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_SPAN_ATTRIBUTE_KEY).getAndRemove();
    if (parentSpan != null) {
      parentScope = TRACER.withSpan(parentSpan);
    }

    final HttpRequest request = (HttpRequest) msg;

    final Span currentSpan = TRACER.getCurrentSpan();
    if (currentSpan.getContext().isValid()) {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(currentSpan);
    } else {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(null);
    }

    final Span span =
        TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(CLIENT).startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      DECORATE.onPeerConnection(span, (InetSocketAddress) ctx.channel().remoteAddress());

      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!request.headers().contains("amz-sdk-invocation-id")) {
        TRACER.getHttpTextFormat().inject(span.getContext(), request.headers(), SETTER);
      }

      ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

      try {
        ctx.write(msg, prm);
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        throw throwable;
      }
    }

    if (null != parentScope) {
      parentScope.close();
    }
  }
}

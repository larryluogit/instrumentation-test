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

package io.opentelemetry.instrumentation.auto.netty.v4_0.client;

import static io.opentelemetry.instrumentation.auto.netty.v4_0.client.NettyHttpClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.netty.v4_0.AttributeKeys;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    Scope parentScope = null;
    Span parentSpan =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_SPAN_ATTRIBUTE_KEY).getAndRemove();
    if (parentSpan != null) {
      parentScope = currentContextWith(parentSpan);
    }

    HttpRequest request = (HttpRequest) msg;

    Span currentSpan = TRACER.getCurrentSpan();
    if (currentSpan.getContext().isValid()) {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(currentSpan);
    } else {
      ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(null);
    }

    Span span = TRACER.startSpan(request);
    NetPeerUtils.setNetPeer(span, (InetSocketAddress) ctx.channel().remoteAddress());
    ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

    try (Scope scope = TRACER.startScope(span, request.headers())) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    } finally {
      if (null != parentScope) {
        parentScope.close();
      }
    }
  }
}

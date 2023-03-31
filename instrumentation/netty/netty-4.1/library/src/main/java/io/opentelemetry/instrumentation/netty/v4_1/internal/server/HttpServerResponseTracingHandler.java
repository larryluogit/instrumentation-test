/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal.server;

import static io.opentelemetry.instrumentation.netty.v4_1.internal.server.HttpServerRequestTracingHandler.HTTP_SERVER_REQUEST;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import io.opentelemetry.instrumentation.netty.v4_1.internal.AttributeKeys;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  private static final AttributeKey<HttpResponse> HTTP_SERVER_RESPONSE =
      AttributeKey.valueOf(HttpServerResponseTracingHandler.class, "http-server-response");

  private final Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter;
  private final HttpServerResponseBeforeCommitHandler beforeCommitHandler;

  public HttpServerResponseTracingHandler(
      Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter,
      HttpServerResponseBeforeCommitHandler beforeCommitHandler) {
    this.instrumenter = instrumenter;
    this.beforeCommitHandler = beforeCommitHandler;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    Attribute<Context> contextAttr = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT);
    Context context = contextAttr.get();
    if (context == null) {
      super.write(ctx, msg, prm);
      return;
    }

    ChannelPromise writePromise;

    if (msg instanceof LastHttpContent) {
      if (prm.isVoid()) {
        // Some frameworks don't actually listen for response completion and optimize for
        // allocations by using a singleton, unnotifiable promise. Hopefully these frameworks don't
        // have observability features or they'd be way off...
        writePromise = ctx.newPromise();
      } else {
        writePromise = prm;
      }

      // Going to finish the span after the write of the last content finishes.
      if (msg instanceof FullHttpResponse) {
        // Headers and body all sent together, we have the response information in the msg.
        beforeCommitHandler.handle(context, (HttpResponse) msg);
        writePromise.addListener(
            future -> end(ctx.channel(), (FullHttpResponse) msg, writePromise));
      } else {
        // Body sent after headers. We stored the response information in the context when
        // encountering HttpResponse (which was not FullHttpResponse since it's not
        // LastHttpContent).
        writePromise.addListener(
            future ->
                end(
                    ctx.channel(),
                    ctx.channel().attr(HTTP_SERVER_RESPONSE).getAndSet(null),
                    writePromise));
      }
    } else {
      writePromise = prm;
      if (msg instanceof HttpResponse) {
        // Headers before body has been sent, store them to use when finishing the span.
        beforeCommitHandler.handle(context, (HttpResponse) msg);
        ctx.channel().attr(HTTP_SERVER_RESPONSE).set((HttpResponse) msg);
      }
    }

    try (Scope ignored = context.makeCurrent()) {
      super.write(ctx, msg, writePromise);
    } catch (Throwable throwable) {
      end(ctx.channel(), null, throwable);
      throw throwable;
    }
  }

  private void end(Channel channel, HttpResponse response, ChannelFuture future) {
    Throwable error = future.isSuccess() ? null : future.cause();
    end(channel, response, error);
  }

  // make sure to remove the server context on end() call
  private void end(Channel channel, @Nullable HttpResponse response, @Nullable Throwable error) {
    Context context = channel.attr(AttributeKeys.SERVER_CONTEXT).getAndSet(null);
    HttpRequestAndChannel request = channel.attr(HTTP_SERVER_REQUEST).getAndSet(null);
    error = NettyErrorHolder.getOrDefault(context, error);
    instrumenter.end(context, request, response, error);
  }
}

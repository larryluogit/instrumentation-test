/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import io.opentelemetry.javaagent.instrumentation.netty.common.Timer;
import javax.annotation.Nullable;

final class NettyErrorOnlyConnectionInstrumenter implements NettyConnectionInstrumenter {

  private final Instrumenter<NettyConnectionRequest, Channel> instrumenter;

  NettyErrorOnlyConnectionInstrumenter(Instrumenter<NettyConnectionRequest, Channel> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public boolean shouldStart(Context parentContext, NettyConnectionRequest request) {
    // the "real" check is done on end() anyway
    return true;
  }

  @Override
  public Context start(Context parentContext, NettyConnectionRequest request) {
    return parentContext.with(Timer.start());
  }

  @Override
  public void end(
      Context context, NettyConnectionRequest request, Channel channel, @Nullable Throwable error) {
    if (error != null && instrumenter.shouldStart(context, request)) {
      Timer timer = Timer.get(context);
      InstrumenterUtil.startAndEnd(
          instrumenter, context, request, channel, error, timer.startTimeNanos(), timer.nowNanos());
    }
  }
}

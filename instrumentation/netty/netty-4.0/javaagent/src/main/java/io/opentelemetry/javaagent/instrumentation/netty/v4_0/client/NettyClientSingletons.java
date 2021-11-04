/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyConnectionInstrumenter;

public final class NettyClientSingletons {

  private static final boolean alwaysCreateConnectSpan =
      Config.get().getBoolean("otel.instrumentation.netty.always-create-connect-span", false);

  private static final Instrumenter<HttpRequestAndChannel, HttpResponse> INSTRUMENTER;
  private static final NettyConnectionInstrumenter CONNECTION_INSTRUMENTER;

  static {
    NettyClientInstrumenterFactory factory =
        new NettyClientInstrumenterFactory("io.opentelemetry.netty-4.0", alwaysCreateConnectSpan);
    INSTRUMENTER = factory.createHttpInstrumenter();
    CONNECTION_INSTRUMENTER = factory.createConnectionInstrumenter();
  }

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static NettyConnectionInstrumenter connectionInstrumenter() {
    return CONNECTION_INSTRUMENTER;
  }

  private NettyClientSingletons() {}
}

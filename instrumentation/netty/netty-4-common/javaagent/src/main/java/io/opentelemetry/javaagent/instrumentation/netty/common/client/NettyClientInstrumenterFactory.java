/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;

public final class NettyClientInstrumenterFactory {

  private final String instrumentationName;
  private final boolean alwaysCreateConnectSpan;
  private final boolean sslTelemetryEnabled;

  public NettyClientInstrumenterFactory(
      String instrumentationName, boolean alwaysCreateConnectSpan, boolean sslTelemetryEnabled) {
    this.instrumentationName = instrumentationName;
    this.alwaysCreateConnectSpan = alwaysCreateConnectSpan;
    this.sslTelemetryEnabled = sslTelemetryEnabled;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesGetter httpClientAttributesGetter =
        new NettyHttpClientAttributesGetter();
    NettyNetClientAttributesGetter netAttributesGetter = new NettyNetClientAttributesGetter();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
        .addAttributesExtractor(HttpClientAttributesExtractor.create(httpClientAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(HttpRequestHeadersSetter.INSTANCE);
  }

  public NettyConnectionInstrumenter createConnectionInstrumenter() {
    NettyConnectNetAttributesGetter netAttributesExtractor = new NettyConnectNetAttributesGetter();
    Instrumenter<NettyConnectionRequest, Channel> instrumenter =
        Instrumenter.<NettyConnectionRequest, Channel>builder(
                GlobalOpenTelemetry.get(), instrumentationName, NettyConnectionRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesExtractor))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .setTimeExtractor(new NettyConnectionTimeExtractor())
            .newInstrumenter(
                alwaysCreateConnectSpan
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return alwaysCreateConnectSpan
        ? new NettyConnectionInstrumenterImpl(instrumenter)
        : new NettyErrorOnlyConnectionInstrumenter(instrumenter);
  }

  public NettySslInstrumenter createSslInstrumenter() {
    NettySslNetAttributesGetter netAttributesExtractor = new NettySslNetAttributesGetter();
    Instrumenter<NettySslRequest, Void> instrumenter =
        Instrumenter.<NettySslRequest, Void>builder(
                GlobalOpenTelemetry.get(), instrumentationName, NettySslRequest::spanName)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesExtractor))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .setTimeExtractor(new NettySslTimeExtractor())
            .newInstrumenter(
                sslTelemetryEnabled
                    ? SpanKindExtractor.alwaysInternal()
                    : SpanKindExtractor.alwaysClient());

    return sslTelemetryEnabled
        ? new NettySslInstrumenterImpl(instrumenter)
        : new NettySslErrorOnlyInstrumenter(instrumenter);
  }
}

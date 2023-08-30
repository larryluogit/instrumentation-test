/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.netty.common.internal.NettyErrorHolder;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.util.function.Consumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class NettyServerInstrumenterFactory {

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      Consumer<HttpServerAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse>>
          extractorConfigurer,
      boolean emitExperimentalHttpServerMetrics) {

    NettyHttpServerAttributesGetter httpAttributesGetter = new NettyHttpServerAttributesGetter();

    HttpServerAttributesExtractorBuilder<HttpRequestAndChannel, HttpResponse> extractorBuilder =
        HttpServerAttributesExtractor.builder(httpAttributesGetter);
    extractorConfigurer.accept(extractorBuilder);

    InstrumenterBuilder<HttpRequestAndChannel, HttpResponse> builder =
        Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
                openTelemetry,
                instrumentationName,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(extractorBuilder.build())
            .addOperationMetrics(HttpServerMetrics.get());
    if (emitExperimentalHttpServerMetrics) {
      builder.addOperationMetrics(HttpServerExperimentalMetrics.get());
    }

    return builder
        .addContextCustomizer((context, request, attributes) -> NettyErrorHolder.init(context))
        .addContextCustomizer(HttpServerRoute.create(httpAttributesGetter))
        .buildServerInstrumenter(HttpRequestHeadersGetter.INSTANCE);
  }

  private NettyServerInstrumenterFactory() {}
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import javax.annotation.Nullable;

public final class VertxClientInstrumenterFactory {

  public static Instrumenter<HttpClientRequest, HttpClientResponse> create(
      String instrumentationName,
      AbstractVertxHttpAttributesExtractor httpAttributesExtractor,
      @Nullable
          NetClientAttributesExtractor<HttpClientRequest, HttpClientResponse>
              netAttributesExtractor) {

    InstrumenterBuilder<HttpClientRequest, HttpClientResponse> builder =
        Instrumenter.<HttpClientRequest, HttpClientResponse>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addRequestMetrics(HttpClientMetrics.get());

    if (netAttributesExtractor != null) {
      builder
          .addAttributesExtractor(netAttributesExtractor)
          .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor));
    }

    return builder.newClientInstrumenter(new HttpRequestHeaderSetter());
  }

  private VertxClientInstrumenterFactory() {}
}

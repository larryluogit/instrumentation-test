/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;

public class AkkaHttpClientSingletons {

  private static final HttpHeaderSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    SETTER = new HttpHeaderSetter(GlobalOpenTelemetry.getPropagators());
    INSTRUMENTER =
        JavaagentHttpClientInstrumenterBuilder.create(
                AkkaHttpUtil.instrumentationName(), new AkkaHttpClientAttributesGetter())
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeaderSetter setter() {
    return SETTER;
  }

  private AkkaHttpClientSingletons() {}
}

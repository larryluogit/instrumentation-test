/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.restlet.data.Request;
import org.restlet.data.Response;

public final class RestletSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-1.1";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    HttpAttributesExtractor<Request, Response> httpAttributesExtractor =
        new RestletHttpAttributesExtractor();
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetAttributesExtractor<Request, Response> netAttributesExtractor =
        new RestletNetAttributesExtractor();

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    INSTRUMENTER =
        Instrumenter.<Request, Response>newBuilder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .newServerInstrumenter(RestletHeadersGetter.GETTER);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static TextMapGetter<Request> getter() {
    return RestletHeadersGetter.GETTER;
  }

  private RestletSingletons() {}
}

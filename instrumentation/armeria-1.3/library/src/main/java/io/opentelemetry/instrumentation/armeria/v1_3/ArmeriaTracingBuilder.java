/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public final class ArmeriaTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.armeria-1.3";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;

  private final List<AttributesExtractor<? super RequestContext, ? super RequestLog>>
      additionalExtractors = new ArrayList<>();

  private final HttpClientAttributesExtractorBuilder<RequestContext, RequestLog>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(ArmeriaHttpClientAttributesGetter.INSTANCE);
  private final HttpServerAttributesExtractorBuilder<RequestContext, RequestLog>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(ArmeriaHttpServerAttributesGetter.INSTANCE);

  private Function<
          SpanStatusExtractor<RequestContext, RequestLog>,
          ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
      statusExtractorTransformer = Function.identity();

  ArmeriaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public ArmeriaTracingBuilder setStatusExtractor(
      Function<
              SpanStatusExtractor<RequestContext, RequestLog>,
              ? extends SpanStatusExtractor<? super RequestContext, ? super RequestLog>>
          statusExtractor) {
    this.statusExtractorTransformer = statusExtractor;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public ArmeriaTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super RequestContext, ? super RequestLog> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public ArmeriaTracingBuilder setPeerService(String peerService) {
    this.peerService = peerService;
    return this;
  }

  /**
   * Configure the HTTP client instrumentation to capture chosen HTTP request and response headers
   * as span attributes.
   *
   * @param capturedHttpClientHeaders An instance of {@link
   *     io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   * @deprecated Use {@link #setCapturedClientRequestHeaders(List)} and {@link
   *     #setCapturedClientResponseHeaders(List)} instead.
   */
  @Deprecated
  public ArmeriaTracingBuilder captureHttpClientHeaders(
      io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
          capturedHttpClientHeaders) {
    httpClientAttributesExtractorBuilder.captureHttpHeaders(capturedHttpClientHeaders);
    return this;
  }

  /**
   * Configures the HTTP client request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public ArmeriaTracingBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public ArmeriaTracingBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configure the HTTP server instrumentation to capture chosen HTTP request and response headers
   * as span attributes.
   *
   * @param capturedHttpServerHeaders An instance of {@link
   *     io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   * @deprecated Use {@link #setCapturedServerRequestHeaders(List)} and {@link
   *     #setCapturedServerResponseHeaders(List)} instead.
   */
  @Deprecated
  public ArmeriaTracingBuilder captureHttpServerHeaders(
      io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
          capturedHttpServerHeaders) {
    httpServerAttributesExtractorBuilder.captureHttpHeaders(capturedHttpServerHeaders);
    return this;
  }

  /**
   * Configures the HTTP server request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public ArmeriaTracingBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public ArmeriaTracingBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  public ArmeriaTracing build() {
    ArmeriaHttpClientAttributesGetter clientAttributesGetter =
        ArmeriaHttpClientAttributesGetter.INSTANCE;
    ArmeriaHttpServerAttributesGetter serverAttributesGetter =
        ArmeriaHttpServerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ClientRequestContext, RequestLog> clientInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            HttpSpanNameExtractor.create(clientAttributesGetter));
    InstrumenterBuilder<ServiceRequestContext, RequestLog> serverInstrumenterBuilder =
        Instrumenter.builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            HttpSpanNameExtractor.create(serverAttributesGetter));

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(instrumenter -> instrumenter.addAttributesExtractors(additionalExtractors));

    ArmeriaNetClientAttributesGetter netClientAttributesGetter =
        new ArmeriaNetClientAttributesGetter();
    NetClientAttributesExtractor<RequestContext, RequestLog> netClientAttributesExtractor =
        NetClientAttributesExtractor.create(netClientAttributesGetter);

    clientInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(clientAttributesGetter)))
        .addAttributesExtractor(netClientAttributesExtractor)
        .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
        .addRequestMetrics(HttpClientMetrics.get());
    serverInstrumenterBuilder
        .setSpanStatusExtractor(
            statusExtractorTransformer.apply(
                HttpSpanStatusExtractor.create(serverAttributesGetter)))
        .addAttributesExtractor(
            NetServerAttributesExtractor.create(new ArmeriaNetServerAttributesGetter()))
        .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
        .addRequestMetrics(HttpServerMetrics.get())
        .addContextCustomizer(HttpRouteHolder.get());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    } else {
      clientInstrumenterBuilder.addAttributesExtractor(
          PeerServiceAttributesExtractor.create(netClientAttributesGetter));
    }

    return new ArmeriaTracing(
        clientInstrumenterBuilder.newClientInstrumenter(ClientRequestContextSetter.INSTANCE),
        serverInstrumenterBuilder.newServerInstrumenter(RequestContextGetter.INSTANCE));
  }
}

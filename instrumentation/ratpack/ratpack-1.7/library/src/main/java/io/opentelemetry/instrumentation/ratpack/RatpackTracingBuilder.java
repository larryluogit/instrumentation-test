/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackHttpNetAttributesGetter;
import io.opentelemetry.instrumentation.ratpack.internal.RatpackNetAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** A builder for {@link RatpackTracing}. */
public final class RatpackTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.ratpack-1.4";

  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super Request, ? super Response>> additionalExtractors =
      new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<RequestSpec, HttpResponse>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(RatpackHttpClientAttributesGetter.INSTANCE);
  private final HttpServerAttributesExtractorBuilder<Request, Response>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(RatpackHttpAttributesGetter.INSTANCE);

  private final List<AttributesExtractor<? super RequestSpec, ? super HttpResponse>>
      additionalHttpClientExtractors = new ArrayList<>();

  RatpackTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public RatpackTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super Request, ? super Response> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  public RatpackTracingBuilder addClientAttributeExtractor(
      AttributesExtractor<? super RequestSpec, ? super HttpResponse> attributesExtractor) {
    additionalHttpClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configure the instrumentation to capture chosen HTTP request and response headers as span
   * attributes.
   *
   * @param capturedHttpHeaders An instance of {@link
   *     io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders} containing the
   *     configured HTTP request and response names.
   * @deprecated Use {@link #setCapturedServerRequestHeaders(List)} and {@link
   *     #setCapturedServerResponseHeaders(List)} instead.
   */
  @Deprecated
  public RatpackTracingBuilder captureHttpHeaders(
      io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders
          capturedHttpHeaders) {
    return captureHttpServerHeaders(capturedHttpHeaders);
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
  public RatpackTracingBuilder captureHttpServerHeaders(
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
  public RatpackTracingBuilder setCapturedServerRequestHeaders(List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP server response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public RatpackTracingBuilder setCapturedServerResponseHeaders(List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
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
  public RatpackTracingBuilder captureHttpClientHeaders(
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
  public RatpackTracingBuilder setCapturedClientRequestHeaders(List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP client response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public RatpackTracingBuilder setCapturedClientResponseHeaders(List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /** Returns a new {@link RatpackTracing} with the configuration of this builder. */
  public RatpackTracing build() {
    RatpackNetAttributesGetter netAttributes = new RatpackNetAttributesGetter();
    RatpackHttpAttributesGetter httpAttributes = RatpackHttpAttributesGetter.INSTANCE;

    Instrumenter<Request, Response> instrumenter =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributes))
            .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(RatpackGetter.INSTANCE);

    return new RatpackTracing(instrumenter, httpClientInstrumenter());
  }

  private Instrumenter<RequestSpec, HttpResponse> httpClientInstrumenter() {
    RatpackHttpNetAttributesGetter netAttributes = new RatpackHttpNetAttributesGetter();
    RatpackHttpClientAttributesGetter httpAttributes = RatpackHttpClientAttributesGetter.INSTANCE;

    return Instrumenter.<RequestSpec, HttpResponse>builder(
            openTelemetry, INSTRUMENTATION_NAME, HttpSpanNameExtractor.create(httpAttributes))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributes))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributes))
        .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
        .addAttributesExtractors(additionalHttpClientExtractors)
        .addRequestMetrics(HttpServerMetrics.get())
        .newClientInstrumenter(RequestHeaderSetter.INSTANCE);
  }
}

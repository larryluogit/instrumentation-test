/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.WebClientNetAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

/** A builder of {@link SpringWebfluxTelemetry}. */
public final class SpringWebfluxTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-webflux-5.3";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ClientRequest, ClientResponse>>
      clientAdditionalExtractors = new ArrayList<>();
  private final List<AttributesExtractor<ServerWebExchange, ServerWebExchange>>
      serverAdditionalExtractors = new ArrayList<>();
  private final HttpClientAttributesExtractorBuilder<ClientRequest, ClientResponse>
      httpClientAttributesExtractorBuilder =
          HttpClientAttributesExtractor.builder(
              WebClientHttpAttributesGetter.INSTANCE, new WebClientNetAttributesGetter());
  private final HttpServerAttributesExtractorBuilder<ServerWebExchange, ServerWebExchange>
      httpServerAttributesExtractorBuilder =
          HttpServerAttributesExtractor.builder(
              WebfluxServerHttpAttributesGetter.INSTANCE,
              WebfluxServerNetAttributesGetter.INSTANCE);

  private boolean captureExperimentalSpanAttributes = false;

  SpringWebfluxTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items for WebClient.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder addClientAttributesExtractor(
      AttributesExtractor<ClientRequest, ClientResponse> attributesExtractor) {
    clientAdditionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP WebClient request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedClientRequestHeaders(
      List<String> requestHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP WebClient response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedClientResponseHeaders(
      List<String> responseHeaders) {
    httpClientAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder addServerAttributesExtractor(
      AttributesExtractor<ServerWebExchange, ServerWebExchange> attributesExtractor) {
    serverAdditionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes from server
   * instrumentation.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedServerRequestHeaders(
      List<String> requestHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes from server
   * instrumentation.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public SpringWebfluxTelemetryBuilder setCapturedServerResponseHeaders(
      List<String> responseHeaders) {
    httpServerAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link SpringWebfluxTelemetry} with the settings of this {@link
   * SpringWebfluxTelemetryBuilder}.
   */
  public SpringWebfluxTelemetry build() {
    WebClientHttpAttributesGetter httpClientAttributesGetter =
        WebClientHttpAttributesGetter.INSTANCE;

    InstrumenterBuilder<ClientRequest, ClientResponse> clientBuilder =
        Instrumenter.<ClientRequest, ClientResponse>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpClientAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesGetter))
            .addAttributesExtractor(httpClientAttributesExtractorBuilder.build())
            .addAttributesExtractors(clientAdditionalExtractors)
            .addOperationMetrics(HttpClientMetrics.get());

    if (captureExperimentalSpanAttributes) {
      clientBuilder.addAttributesExtractor(new WebClientExperimentalAttributesExtractor());
    }

    // headers are injected elsewhere; ClientRequest is immutable
    Instrumenter<ClientRequest, ClientResponse> clientInstrumenter =
        clientBuilder.buildInstrumenter(alwaysClient());

    WebfluxServerHttpAttributesGetter serverAttributesGetter =
        WebfluxServerHttpAttributesGetter.INSTANCE;
    SpanNameExtractor<ServerWebExchange> serverSpanNameExtractor =
        HttpSpanNameExtractor.create(serverAttributesGetter);

    Instrumenter<ServerWebExchange, ServerWebExchange> serverInstrumenter =
        Instrumenter.<ServerWebExchange, ServerWebExchange>builder(
                openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(serverAttributesGetter))
            .addAttributesExtractor(httpServerAttributesExtractorBuilder.build())
            .addAttributesExtractors(serverAdditionalExtractors)
            .buildServerInstrumenter(WebfluxTextMapGetter.INSTANCE);

    return new SpringWebfluxTelemetry(
        clientInstrumenter, serverInstrumenter, openTelemetry.getPropagators());
  }
}

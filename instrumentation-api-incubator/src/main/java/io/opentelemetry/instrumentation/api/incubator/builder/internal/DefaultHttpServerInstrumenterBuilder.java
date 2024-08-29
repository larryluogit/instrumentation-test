/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractorBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> {

  private final String instrumentationName;
  private final OpenTelemetry openTelemetry;

  private final List<AttributesExtractor<? super REQUEST, ? super RESPONSE>> additionalExtractors =
      new ArrayList<>();
  private Function<
          SpanStatusExtractor<? super REQUEST, ? super RESPONSE>,
          ? extends SpanStatusExtractor<? super REQUEST, ? super RESPONSE>>
      statusExtractorTransformer = Function.identity();
  private final HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE>
      httpAttributesExtractorBuilder;
  private final HttpSpanNameExtractorBuilder<REQUEST> httpSpanNameExtractorBuilder;

  private final TextMapGetter<REQUEST> headerGetter;
  private Function<SpanNameExtractor<? super REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
      spanNameExtractorTransformer = Function.identity();
  private final HttpServerRouteBuilder<REQUEST> httpServerRouteBuilder;
  private final HttpServerAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private boolean emitExperimentalHttpServerMetrics = false;
  private Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer = b -> {};

  public DefaultHttpServerInstrumenterBuilder(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      HttpServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      TextMapGetter<REQUEST> headerGetter) {
    this.instrumentationName = instrumentationName;
    this.openTelemetry = openTelemetry;
    httpAttributesExtractorBuilder = HttpServerAttributesExtractor.builder(attributesGetter);
    httpSpanNameExtractorBuilder = HttpSpanNameExtractor.builder(attributesGetter);
    httpServerRouteBuilder = HttpServerRoute.builder(attributesGetter);
    this.attributesGetter = attributesGetter;
    this.headerGetter = headerGetter;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> addAttributesExtractor(
      AttributesExtractor<? super REQUEST, ? super RESPONSE> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setStatusExtractor(
      Function<
              SpanStatusExtractor<? super REQUEST, ? super RESPONSE>,
              ? extends SpanStatusExtractor<? super REQUEST, ? super RESPONSE>>
          statusExtractor) {
    this.statusExtractorTransformer = statusExtractor;
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    httpAttributesExtractorBuilder.setCapturedRequestHeaders(requestHeaders);
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    httpAttributesExtractorBuilder.setCapturedResponseHeaders(responseHeaders);
    return this;
  }

  /**
   * Configures the instrumentation to recognize an alternative set of HTTP request methods.
   *
   * <p>By default, this instrumentation defines "known" methods as the ones listed in <a
   * href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC9110</a> and the PATCH
   * method defined in <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC5789</a>.
   *
   * <p>Note: calling this method <b>overrides</b> the default known method sets completely; it does
   * not supplement it.
   *
   * @param knownMethods A set of recognized HTTP request methods.
   * @see HttpServerAttributesExtractorBuilder#setKnownMethods(Set)
   */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setKnownMethods(
      Set<String> knownMethods) {
    httpAttributesExtractorBuilder.setKnownMethods(knownMethods);
    httpSpanNameExtractorBuilder.setKnownMethods(knownMethods);
    httpServerRouteBuilder.setKnownMethods(knownMethods);
    return this;
  }

  /**
   * Configures the instrumentation to emit experimental HTTP server metrics.
   *
   * @param emitExperimentalHttpServerMetrics {@code true} if the experimental HTTP server metrics
   *     are to be emitted.
   */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>
      setEmitExperimentalHttpServerMetrics(boolean emitExperimentalHttpServerMetrics) {
    this.emitExperimentalHttpServerMetrics = emitExperimentalHttpServerMetrics;
    return this;
  }

  /** Sets custom {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setSpanNameExtractor(
      Function<SpanNameExtractor<? super REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
          spanNameExtractorTransformer) {
    this.spanNameExtractorTransformer = spanNameExtractorTransformer;
    return this;
  }

  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setBuilderCustomizer(
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> builderCustomizer) {
    this.builderCustomizer = builderCustomizer;
    return this;
  }

  public Instrumenter<REQUEST, RESPONSE> build() {
    SpanNameExtractor<? super REQUEST> spanNameExtractor =
        spanNameExtractorTransformer.apply(httpSpanNameExtractorBuilder.build());

    InstrumenterBuilder<REQUEST, RESPONSE> builder =
        Instrumenter.<REQUEST, RESPONSE>builder(
                openTelemetry, instrumentationName, spanNameExtractor)
            .setSpanStatusExtractor(
                statusExtractorTransformer.apply(HttpSpanStatusExtractor.create(attributesGetter)))
            .addAttributesExtractor(httpAttributesExtractorBuilder.build())
            .addAttributesExtractors(additionalExtractors)
            .addContextCustomizer(httpServerRouteBuilder.build())
            .addOperationMetrics(HttpServerMetrics.get());
    if (emitExperimentalHttpServerMetrics) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(attributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    builderCustomizer.accept(builder);

    if (headerGetter != null) {
      return builder.buildServerInstrumenter(headerGetter);
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> configure(CommonConfig config) {
    set(config::getKnownHttpRequestMethods, this::setKnownMethods);
    set(config::getServerRequestHeaders, this::setCapturedRequestHeaders);
    set(config::getServerResponseHeaders, this::setCapturedResponseHeaders);
    set(
        config::shouldEmitExperimentalHttpServerTelemetry,
        this::setEmitExperimentalHttpServerMetrics);
    return this;
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }
}

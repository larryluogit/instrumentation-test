/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.builder.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreCommonConfig;
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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

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

  @Nullable private TextMapGetter<REQUEST> headerGetter;
  private Function<SpanNameExtractor<REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
      spanNameExtractorTransformer = Function.identity();
  private final HttpServerRouteBuilder<REQUEST> httpServerRouteBuilder;
  private final HttpServerAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private boolean emitExperimentalHttpServerMetrics = false;

  public DefaultHttpServerInstrumenterBuilder(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      HttpServerAttributesGetter<REQUEST, RESPONSE> attributesGetter) {
    this.instrumentationName = instrumentationName;
    this.openTelemetry = openTelemetry;
    httpAttributesExtractorBuilder = HttpServerAttributesExtractor.builder(attributesGetter);
    httpSpanNameExtractorBuilder = HttpSpanNameExtractor.builder(attributesGetter);
    httpServerRouteBuilder = HttpServerRoute.builder(attributesGetter);
    this.attributesGetter = attributesGetter;
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

  @CanIgnoreReturnValue
  public DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> setHeaderGetter(
      @Nullable TextMapGetter<REQUEST> headerGetter) {
    this.headerGetter = headerGetter;
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
      Function<SpanNameExtractor<REQUEST>, ? extends SpanNameExtractor<? super REQUEST>>
          spanNameExtractorTransformer) {
    this.spanNameExtractorTransformer = spanNameExtractorTransformer;
    return this;
  }

  public Instrumenter<REQUEST, RESPONSE> build() {
    return build(b -> {});
  }

  public Instrumenter<REQUEST, RESPONSE> build(
      Consumer<InstrumenterBuilder<REQUEST, RESPONSE>> instrumenterBuilderConsumer) {

    InstrumenterBuilder<REQUEST, RESPONSE> builder = builder();
    instrumenterBuilderConsumer.accept(builder);

    if (headerGetter != null) {
      return builder.buildServerInstrumenter(headerGetter);
    }
    return builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
  }

  public InstrumenterBuilder<REQUEST, RESPONSE> builder() {
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

    return builder;
  }

  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> unwrapAndConfigure(
          CoreCommonConfig config, Object builder) {
    DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> defaultBuilder = unwrapBuilder(builder);
    set(config::getKnownHttpRequestMethods, defaultBuilder::setKnownMethods);
    set(config::getServerRequestHeaders, defaultBuilder::setCapturedRequestHeaders);
    set(config::getServerResponseHeaders, defaultBuilder::setCapturedResponseHeaders);
    set(
        config::shouldEmitExperimentalHttpServerTelemetry,
        defaultBuilder::setEmitExperimentalHttpServerMetrics);
    return defaultBuilder;
  }

  private static <T> void set(Supplier<T> supplier, Consumer<T> consumer) {
    T t = supplier.get();
    if (t != null) {
      consumer.accept(t);
    }
  }

  /**
   * This method is used to access the builder field of the builder object.
   *
   * <p>This approach allows us to re-use the existing builder classes from the library modules
   */
  @SuppressWarnings("unchecked")
  private static <REQUEST, RESPONSE>
      DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE> unwrapBuilder(Object builder) {
    if (builder instanceof DefaultHttpServerInstrumenterBuilder<?, ?>) {
      return (DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>) builder;
    }
    Class<?> builderClass = builder.getClass();
    try {
      Field field = builderClass.getDeclaredField("serverBuilder");
      field.setAccessible(true);
      return (DefaultHttpServerInstrumenterBuilder<REQUEST, RESPONSE>) field.get(builder);
    } catch (Exception e) {
      throw new IllegalStateException("Could not access serverBuilder field in " + builderClass, e);
    }
  }
}

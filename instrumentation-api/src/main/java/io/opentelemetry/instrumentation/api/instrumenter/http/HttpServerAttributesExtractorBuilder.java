/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;

/** A builder of {@link HttpServerAttributesExtractor}. */
@SuppressWarnings("deprecation") // suppress CapturedHttpHeaders deprecation
public final class HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final HttpServerAttributesGetter<REQUEST, RESPONSE> getter;
  CapturedHttpHeaders capturedHttpHeaders = CapturedHttpHeaders.server(Config.get());

  HttpServerAttributesExtractorBuilder(HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Configures the HTTP headers that will be captured as span attributes.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   * @deprecated Use {@link #setCapturedRequestHeaders(List)} and {@link
   *     #setCapturedResponseHeaders(List)} instead.
   */
  @Deprecated
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> captureHttpHeaders(
      CapturedHttpHeaders capturedHttpHeaders) {
    this.capturedHttpHeaders = capturedHttpHeaders;
    return this;
  }

  /**
   * Configures the HTTP request headers that will be captured as span attributes.
   *
   * @param requestHeaders A list of HTTP header names.
   */
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedRequestHeaders(
      List<String> requestHeaders) {
    this.capturedHttpHeaders =
        CapturedHttpHeaders.create(requestHeaders, capturedHttpHeaders.responseHeaders());
    return this;
  }

  /**
   * Configures the HTTP response headers that will be captured as span attributes.
   *
   * @param responseHeaders A list of HTTP header names.
   */
  public HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedResponseHeaders(
      List<String> responseHeaders) {
    this.capturedHttpHeaders =
        CapturedHttpHeaders.create(capturedHttpHeaders.requestHeaders(), responseHeaders);
    return this;
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractor} with the settings of this {@link
   * HttpServerAttributesExtractorBuilder}.
   */
  public HttpServerAttributesExtractor<REQUEST, RESPONSE> build() {
    return new HttpServerAttributesExtractor<>(getter, capturedHttpHeaders);
  }
}

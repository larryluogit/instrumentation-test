/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-client">HTTP
 * client attributes</a>. Instrumentation of HTTP client frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpClientAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpClientAttributesGetter<REQUEST, RESPONSE>> {

  /** Creates the HTTP client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> HttpClientAttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return create(getter, CapturedHttpHeaders.client(Config.get()));
  }

  // TODO: there should be a builder for all optional attributes
  /**
   * Creates the HTTP client attributes extractor.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   */
  public static <REQUEST, RESPONSE> HttpClientAttributesExtractor<REQUEST, RESPONSE> create(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    return new HttpClientAttributesExtractor<>(getter, capturedHttpHeaders);
  }

  private HttpClientAttributesExtractor(
      HttpClientAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    super(getter, capturedHttpHeaders);
  }

  @Override
  public void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);
    set(attributes, SemanticAttributes.HTTP_URL, getter.url(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    super.onEnd(attributes, request, response, error);
    set(attributes, SemanticAttributes.HTTP_FLAVOR, getter.flavor(request, response));
  }
}

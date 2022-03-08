/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedForHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractClientIpFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedHeader;
import static io.opentelemetry.instrumentation.api.instrumenter.http.ForwardedHeaderParser.extractProtoFromForwardedProtoHeader;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#http-server">HTTP
 * server attributes</a>. Instrumentation of HTTP server frameworks should extend this class,
 * defining {@link REQUEST} and {@link RESPONSE} with the actual request / response types of the
 * instrumented library. If an attribute is not available in this library, it is appropriate to
 * return {@code null} from the protected attribute methods, but implement as many as possible for
 * best compliance with the OpenTelemetry specification.
 */
public final class HttpServerAttributesExtractor<REQUEST, RESPONSE>
    extends HttpCommonAttributesExtractor<
        REQUEST, RESPONSE, HttpServerAttributesGetter<REQUEST, RESPONSE>> {

  /** Creates the HTTP server attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return builder(getter).build();
  }

  /**
   * Creates the HTTP server attributes extractor.
   *
   * @param capturedHttpHeaders A configuration object specifying which HTTP request and response
   *     headers should be captured as span attributes.
   * @deprecated Use {@link #builder(HttpServerAttributesGetter)} instead.
   */
  @Deprecated
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractor<REQUEST, RESPONSE> create(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    return builder(getter).captureHttpHeaders(capturedHttpHeaders).build();
  }

  /**
   * Returns a new {@link HttpServerAttributesExtractorBuilder} that can be used to configure the
   * HTTP client attributes extractor.
   */
  public static <REQUEST, RESPONSE> HttpServerAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new HttpServerAttributesExtractorBuilder<>(getter);
  }

  private final Function<Context, String> httpRouteHolderGetter;

  HttpServerAttributesExtractor(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders) {
    this(getter, capturedHttpHeaders, HttpRouteHolder::getRoute);
  }

  // visible for tests
  HttpServerAttributesExtractor(
      HttpServerAttributesGetter<REQUEST, RESPONSE> getter,
      CapturedHttpHeaders capturedHttpHeaders,
      Function<Context, String> httpRouteHolderGetter) {
    super(getter, capturedHttpHeaders);
    this.httpRouteHolderGetter = httpRouteHolderGetter;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    set(attributes, SemanticAttributes.HTTP_FLAVOR, getter.flavor(request));
    String forwardedProto = forwardedProto(request);
    set(
        attributes,
        SemanticAttributes.HTTP_SCHEME,
        forwardedProto != null ? forwardedProto : getter.scheme(request));
    set(attributes, SemanticAttributes.HTTP_HOST, host(request));
    set(attributes, SemanticAttributes.HTTP_TARGET, getter.target(request));
    set(attributes, SemanticAttributes.HTTP_ROUTE, getter.route(request));
    set(attributes, SemanticAttributes.HTTP_SERVER_NAME, getter.serverName(request));
    set(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientIp(request));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    super.onEnd(attributes, context, request, response, error);
    set(attributes, SemanticAttributes.HTTP_ROUTE, httpRouteHolderGetter.apply(context));
  }

  @Nullable
  private String host(REQUEST request) {
    return firstHeaderValue(getter.requestHeader(request, "host"));
  }

  @Nullable
  private String forwardedProto(REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.requestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractProtoFromForwardedHeader(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-Proto
    forwarded = firstHeaderValue(getter.requestHeader(request, "x-forwarded-proto"));
    if (forwarded != null) {
      return extractProtoFromForwardedProtoHeader(forwarded);
    }

    return null;
  }

  @Nullable
  private String clientIp(REQUEST request) {
    // try Forwarded
    String forwarded = firstHeaderValue(getter.requestHeader(request, "forwarded"));
    if (forwarded != null) {
      forwarded = extractClientIpFromForwardedHeader(forwarded);
      if (forwarded != null) {
        return forwarded;
      }
    }

    // try X-Forwarded-For
    forwarded = firstHeaderValue(getter.requestHeader(request, "x-forwarded-for"));
    if (forwarded != null) {
      return extractClientIpFromForwardedForHeader(forwarded);
    }

    return null;
  }
}

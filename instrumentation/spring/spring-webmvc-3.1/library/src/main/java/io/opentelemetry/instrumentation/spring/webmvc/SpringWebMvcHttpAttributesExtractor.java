/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class SpringWebMvcHttpAttributesExtractor
    extends HttpServerAttributesExtractor<HttpServletRequest, HttpServletResponse> {
  @Override
  protected @Nullable String method(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  protected @Nullable String userAgent(HttpServletRequest request) {
    return request.getHeader("user-agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return null;
  }

  @Override
  protected @Nullable String flavor(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return request.getProtocol();
  }

  @Override
  protected Integer statusCode(HttpServletRequest request, HttpServletResponse response) {
    return response.getStatus();
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpServletRequest request, HttpServletResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpServletRequest request, HttpServletResponse response) {
    return null;
  }

  @Override
  protected @Nullable String url(HttpServletRequest request) {
    return null;
  }

  @Override
  protected @Nullable String target(HttpServletRequest request) {
    String target = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  protected @Nullable String host(HttpServletRequest request) {
    return request.getHeader("host");
  }

  @Override
  protected @Nullable String route(HttpServletRequest request) {
    return null;
  }

  @Override
  protected @Nullable String scheme(HttpServletRequest request) {
    return request.getScheme();
  }

  @Override
  protected @Nullable String serverName(
      HttpServletRequest request, @Nullable HttpServletResponse response) {
    return null;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebNetAttributesExtractor
    extends NetServerAttributesExtractor<HttpRequest, ClientHttpResponse> {
  @Override
  public String transport(HttpRequest httpRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(HttpRequest httpRequest) {
    return httpRequest.getURI().getHost();
  }

  @Override
  public Integer peerPort(HttpRequest httpRequest) {
    return httpRequest.getURI().getPort();
  }

  @Override
  public @Nullable String peerIp(HttpRequest httpRequest) {
    return null;
  }
}

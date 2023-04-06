/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class GoogleHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getTransport(HttpRequest request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public String getPeerName(HttpRequest request) {
    return request.getUrl().getHost();
  }

  @Override
  public Integer getPeerPort(HttpRequest request) {
    return request.getUrl().getPort();
  }
}

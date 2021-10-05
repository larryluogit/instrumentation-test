/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpAsyncClientNetAttributesExtractor
    extends NetAttributesServerExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String transport(ApacheHttpClientRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(ApacheHttpClientRequest request) {
    return request.getPeerName();
  }

  @Override
  public Integer peerPort(ApacheHttpClientRequest request) {
    return request.getPeerPort();
  }

  @Override
  public @Nullable String peerIp(ApacheHttpClientRequest request) {
    return null;
  }
}

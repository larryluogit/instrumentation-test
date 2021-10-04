/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticTransportNetAttributesExtractor
    extends NetAttributesExtractor<ElasticTransportRequest, ActionResponse> {
  public ElasticTransportNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_END);
  }

  @Override
  public @Nullable String transport(ElasticTransportRequest elasticTransportRequest) {
    return null;
  }

  @Override
  public @Nullable String peerName(
      ElasticTransportRequest elasticTransportRequest, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getHost();
    }
    return null;
  }

  @Override
  public @Nullable Integer peerPort(
      ElasticTransportRequest elasticTransportRequest, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getPort();
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(
      ElasticTransportRequest elasticTransportRequest, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getAddress();
    }
    return null;
  }
}

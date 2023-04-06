/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.Inet6Address;
import javax.annotation.Nullable;
import org.opensearch.client.Response;

final class OpenSearchRestNetResponseAttributesGetter
    implements NetClientAttributesGetter<OpenSearchRestRequest, Response> {

  @Override
  public String getTransport(OpenSearchRestRequest request, Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(OpenSearchRestRequest request, @Nullable Response response) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(OpenSearchRestRequest request, @Nullable Response response) {
    return null;
  }

  @Override
  @Nullable
  public String getPeerName(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Integer getPeerPort(OpenSearchRestRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockFamily(
      OpenSearchRestRequest elasticsearchRestRequest, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Override
  @Nullable
  public String getSockPeerAddr(OpenSearchRestRequest request, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() != null) {
      return response.getHost().getAddress().getHostAddress();
    }
    return null;
  }
}

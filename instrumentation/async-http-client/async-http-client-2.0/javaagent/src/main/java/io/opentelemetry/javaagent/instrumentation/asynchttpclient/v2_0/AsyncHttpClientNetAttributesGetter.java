/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.asynchttpclient.Response;

final class AsyncHttpClientNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<RequestContext, Response> {

  @Override
  public String transport(RequestContext request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(RequestContext requestContext, @Nullable Response response) {
    return requestContext.getRequest().getUri().getHost();
  }

  @Override
  public Integer peerPort(RequestContext requestContext, @Nullable Response response) {
    return requestContext.getRequest().getUri().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      RequestContext request, @Nullable Response response) {
    if (response != null && response.getRemoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) response.getRemoteAddress();
    }
    return null;
  }
}

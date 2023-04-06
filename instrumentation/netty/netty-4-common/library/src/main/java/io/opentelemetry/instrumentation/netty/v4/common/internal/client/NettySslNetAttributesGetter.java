/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class NettySslNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<NettySslRequest, Void> {

  @Override
  public String getTransport(NettySslRequest request, @Nullable Void unused) {
    return request.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(NettySslRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(NettySslRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(NettySslRequest request, @Nullable Void unused) {
    if (request.remoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) request.remoteAddress();
    }
    return null;
  }
}

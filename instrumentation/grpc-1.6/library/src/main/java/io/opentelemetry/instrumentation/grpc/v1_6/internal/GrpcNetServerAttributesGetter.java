/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<GrpcRequest> {
  @Override
  @Nullable
  public InetSocketAddress getAddress(GrpcRequest request) {
    SocketAddress address = request.getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Override
  public String transport(GrpcRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}

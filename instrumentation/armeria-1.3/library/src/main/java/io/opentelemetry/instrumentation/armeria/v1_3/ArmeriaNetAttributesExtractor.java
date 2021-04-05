/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ArmeriaNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<RequestContext, RequestLog> {

  @Override
  protected String transport(RequestContext requestContext) {
    return SemanticAttributes.NetTransportValues.IP_TCP.getValue();
  }

  @Override
  @Nullable
  protected InetSocketAddress getAddress(RequestContext requestContext, RequestLog requestLog) {
    SocketAddress address = requestContext.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}

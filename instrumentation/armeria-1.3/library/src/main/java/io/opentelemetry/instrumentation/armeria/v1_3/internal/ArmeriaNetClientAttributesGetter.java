/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ArmeriaNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<RequestContext, RequestLog> {

  @Override
  public String getTransport(RequestContext ctx, @Nullable RequestLog requestLog) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String getProtocolName(RequestContext ctx, @Nullable RequestLog requestLog) {
    return "http";
  }

  @Override
  public String getProtocolVersion(RequestContext ctx, @Nullable RequestLog requestLog) {
    SessionProtocol protocol = ctx.sessionProtocol();
    return protocol.isMultiplex() ? "2.0" : "1.1";
  }

  @Nullable
  @Override
  public String getPeerName(RequestContext ctx) {
    return request(ctx).uri().getHost();
  }

  @Override
  public Integer getPeerPort(RequestContext ctx) {
    return request(ctx).uri().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      RequestContext ctx, @Nullable RequestLog requestLog) {
    SocketAddress address = ctx.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  private static HttpRequest request(RequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import ratpack.handling.Context;
import ratpack.http.Request;
import ratpack.server.PublicAddress;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackNetServerAttributesGetter implements NetServerAttributesGetter<Request> {
  @Override
  public String getTransport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(Request request) {
    PublicAddress publicAddress = getPublicAddress(request);
    return publicAddress == null ? null : publicAddress.get().getHost();
  }

  @Nullable
  @Override
  public Integer getHostPort(Request request) {
    PublicAddress publicAddress = getPublicAddress(request);
    return publicAddress == null ? null : publicAddress.get().getPort();
  }

  private static PublicAddress getPublicAddress(Request request) {
    Context ratpackContext = request.get(Context.class);
    if (ratpackContext == null) {
      return null;
    }
    return ratpackContext.get(PublicAddress.class);
  }

  @Override
  public Integer getSockPeerPort(Request request) {
    return request.getRemoteAddress().getPort();
  }
}

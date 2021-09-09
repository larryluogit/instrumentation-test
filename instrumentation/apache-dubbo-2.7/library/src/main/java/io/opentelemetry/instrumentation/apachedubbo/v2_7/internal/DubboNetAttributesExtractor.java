/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import java.net.InetSocketAddress;
import org.apache.dubbo.rpc.Result;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class DubboNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<DubboRequest, Result> {

  @Override
  public @Nullable InetSocketAddress getAddress(DubboRequest request, @Nullable Result result) {
    return request.context().getRemoteAddress();
  }

  @Override
  public @Nullable String transport(DubboRequest request) {
    return null;
  }
}

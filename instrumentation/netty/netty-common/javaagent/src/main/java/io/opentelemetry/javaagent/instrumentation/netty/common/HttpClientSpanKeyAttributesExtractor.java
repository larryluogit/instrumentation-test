/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import javax.annotation.Nullable;

/**
 * Attributes extractor that pretends it's a {@link HttpClientAttributesExtractor} so that error
 * only CONNECT spans can be suppressed by higher level HTTP clients based on netty.
 */
public enum HttpClientSpanKeyAttributesExtractor
    implements AttributesExtractor<NettyConnectionRequest, Object>, SpanKeyProvider {
  INSTANCE;

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      NettyConnectionRequest nettyConnectionRequest) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      NettyConnectionRequest nettyConnectionRequest,
      @Nullable Object channel,
      @Nullable Throwable error) {}

  @Override
  public SpanKey internalGetSpanKey() {
    return SpanKey.HTTP_CLIENT;
  }
}

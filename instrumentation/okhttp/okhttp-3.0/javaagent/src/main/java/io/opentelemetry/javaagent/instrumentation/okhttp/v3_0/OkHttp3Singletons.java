/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpNetAttributesGetter;
import okhttp3.Interceptor;

/** Holder of singleton interceptors for adding to instrumented clients. */
public final class OkHttp3Singletons {

  @SuppressWarnings("deprecation") // we're still using the interceptor on its own for now
  public static final Interceptor TRACING_INTERCEPTOR =
      OkHttpTelemetry.builder(GlobalOpenTelemetry.get())
          .addAttributesExtractor(
              PeerServiceAttributesExtractor.create(new OkHttpNetAttributesGetter()))
          .build()
          .newInterceptor();

  private OkHttp3Singletons() {}
}

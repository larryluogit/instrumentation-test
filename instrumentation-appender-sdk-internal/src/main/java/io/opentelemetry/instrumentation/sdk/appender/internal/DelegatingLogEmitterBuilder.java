/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sdk.appender.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitter;
import io.opentelemetry.instrumentation.api.appender.internal.LogEmitterBuilder;

final class DelegatingLogEmitterBuilder implements LogEmitterBuilder {

  private final io.opentelemetry.sdk.logs.LogEmitterBuilder delegate;

  DelegatingLogEmitterBuilder(io.opentelemetry.sdk.logs.LogEmitterBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  @CanIgnoreReturnValue
  public LogEmitterBuilder setSchemaUrl(String schemaUrl) {
    delegate.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion) {
    delegate.setInstrumentationVersion(instrumentationVersion);
    return this;
  }

  @Override
  public LogEmitter build() {
    return new DelegatingLogEmitter(delegate.build());
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.concurrent.TimeUnit;

/** A builder of {@link OpenTelemetryMeterRegistry}. */
public final class OpenTelemetryMeterRegistryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  private final OpenTelemetry openTelemetry;
  private Clock clock = Clock.SYSTEM;
  private TimeUnit baseTimeUnit =
      TimeUnitHelper.parseConfigValue(
          Config.get().getString("otel.instrumentation.micrometer.base-time-unit"));

  OpenTelemetryMeterRegistryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets a custom {@link Clock}. Useful for testing. */
  public OpenTelemetryMeterRegistryBuilder setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  /** Sets the base time unit. */
  public OpenTelemetryMeterRegistryBuilder setBaseTimeUnit(TimeUnit baseTimeUnit) {
    this.baseTimeUnit = baseTimeUnit;
    return this;
  }

  /**
   * Returns a new {@link OpenTelemetryMeterRegistry} with the settings of this {@link
   * OpenTelemetryMeterRegistryBuilder}.
   */
  public MeterRegistry build() {
    return new OpenTelemetryMeterRegistry(
        clock, baseTimeUnit, openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME));
  }
}

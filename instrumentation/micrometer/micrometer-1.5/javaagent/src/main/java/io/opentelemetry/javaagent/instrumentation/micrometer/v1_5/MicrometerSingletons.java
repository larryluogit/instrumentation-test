/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CoreInstrumentationConfig;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class MicrometerSingletons {

  private static final MeterRegistry METER_REGISTRY;

  static {
    CoreInstrumentationConfig config = InstrumentationConfig.get();
    METER_REGISTRY =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setPrometheusMode(
                config.getBoolean("otel.instrumentation.micrometer.prometheus-mode.enabled", false))
            .setBaseTimeUnit(
                TimeUnitParser.parseConfigValue(
                    config.getString("otel.instrumentation.micrometer.base-time-unit")))
            .setMicrometerHistogramGaugesEnabled(
                config.getBoolean(
                    "otel.instrumentation.micrometer.histogram-gauges.enabled", false))
            .build();
  }

  public static MeterRegistry meterRegistry() {
    return METER_REGISTRY;
  }

  private MicrometerSingletons() {}
}

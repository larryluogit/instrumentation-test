/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.statisticInstrumentName;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static io.opentelemetry.instrumentation.micrometer.v1_5.TimeUnitHelper.getUnitString;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryLongTaskTimer extends DefaultLongTaskTimer implements RemovableMeter {

  private final TimeUnit baseTimeUnit;
  private final DistributionStatisticConfig distributionStatisticConfig;
  // TODO: use bound instruments when they're available
  private final DoubleHistogram otelHistogram;
  private final LongUpDownCounter otelActiveTasksCounter;
  private final Attributes attributes;

  private volatile boolean removed = false;

  OpenTelemetryLongTaskTimer(
      Id id,
      NamingConvention namingConvention,
      Clock clock,
      TimeUnit baseTimeUnit,
      DistributionStatisticConfig distributionStatisticConfig,
      Meter otelMeter) {
    super(id, clock, baseTimeUnit, distributionStatisticConfig, false);

    this.baseTimeUnit = baseTimeUnit;
    this.distributionStatisticConfig = distributionStatisticConfig;

    this.otelHistogram =
        otelMeter
            .histogramBuilder(name(id, namingConvention))
            .setDescription(description(id))
            .setUnit(getUnitString(baseTimeUnit))
            .build();
    this.otelActiveTasksCounter =
        otelMeter
            .upDownCounterBuilder(
                statisticInstrumentName(id, Statistic.ACTIVE_TASKS, namingConvention))
            .setDescription(description(id))
            .setUnit("tasks")
            .build();
    this.attributes = tagsAsAttributes(id, namingConvention);
  }

  @Override
  public Sample start() {
    Sample original = super.start();
    if (removed) {
      return original;
    }

    otelActiveTasksCounter.add(1, attributes);
    return new OpenTelemetrySample(original);
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    removed = true;
  }

  boolean isUsingMicrometerHistograms() {
    return distributionStatisticConfig.isPublishingPercentiles()
        || distributionStatisticConfig.isPublishingHistogram();
  }

  private final class OpenTelemetrySample extends Sample {

    private final Sample original;
    private volatile boolean stopped = false;

    private OpenTelemetrySample(Sample original) {
      this.original = original;
    }

    @Override
    public long stop() {
      if (stopped) {
        return -1;
      }
      stopped = true;
      long durationNanos = original.stop();
      if (!removed) {
        otelActiveTasksCounter.add(-1, attributes);
        double time = TimeUtils.nanosToUnit(durationNanos, baseTimeUnit);
        otelHistogram.record(time, attributes);
      }
      return durationNanos;
    }

    @Override
    public double duration(TimeUnit unit) {
      return stopped ? -1 : original.duration(unit);
    }
  }
}

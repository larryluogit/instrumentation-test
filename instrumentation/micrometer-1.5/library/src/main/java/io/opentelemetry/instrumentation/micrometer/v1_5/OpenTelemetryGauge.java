/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.opentelemetry.instrumentation.micrometer.v1_5.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryGauge<T> implements Gauge, RemovableMeter {

  private final Id id;
  private final AsyncMeasurementHandle gaugeMeasurementHandle;

  OpenTelemetryGauge(
      Id id,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;

    gaugeMeasurementHandle =
        asyncInstrumentRegistry.buildGauge(id, tagsAsAttributes(id), obj, objMetric);
  }

  @Override
  public double value() {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public Id getId() {
    return id;
  }

  @Override
  public void onRemove() {
    gaugeMeasurementHandle.remove();
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(@Nullable Object o) {
    return MeterEquivalence.equals(this, o);
  }

  @Override
  public int hashCode() {
    return MeterEquivalence.hashCode(this);
  }
}

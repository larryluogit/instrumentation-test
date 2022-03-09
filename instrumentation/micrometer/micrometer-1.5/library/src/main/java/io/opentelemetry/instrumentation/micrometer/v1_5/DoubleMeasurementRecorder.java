/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class DoubleMeasurementRecorder<T> implements Consumer<ObservableDoubleMeasurement> {

  private final WeakReference<T> objWeakRef;
  private final ToDoubleFunction<T> metricFunction;
  private final Attributes attributes;

  DoubleMeasurementRecorder(
      @Nullable T obj, ToDoubleFunction<T> metricFunction, Attributes attributes) {
    this.objWeakRef = new WeakReference<>(obj);
    this.metricFunction = metricFunction;
    this.attributes = attributes;
  }

  @Override
  public void accept(ObservableDoubleMeasurement measurement) {
    T obj = objWeakRef.get();
    if (obj != null) {
      measurement.record(metricFunction.applyAsDouble(obj), attributes);
    }
  }
}

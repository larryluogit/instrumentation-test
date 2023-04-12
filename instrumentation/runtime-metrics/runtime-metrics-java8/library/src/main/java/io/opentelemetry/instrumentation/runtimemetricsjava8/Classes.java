/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetricsjava8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers measurements that generate metrics about JVM classes.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Classes.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   process.runtime.jvm.classes.loaded 100
 *   process.runtime.jvm.classes.unloaded 2
 *   process.runtime.jvm.classes.current_loaded 98
 * </pre>
 */
public final class Classes {

  // Visible for testing
  static final Classes INSTANCE = new Classes();
  private static final List<AutoCloseable> observables = new ArrayList<>();

  /** Register observers for java runtime class metrics. */
  public static void registerObservers(OpenTelemetry openTelemetry) {
    INSTANCE.registerObservers(openTelemetry, ManagementFactory.getClassLoadingMXBean());
  }

  // Visible for testing
  void registerObservers(OpenTelemetry openTelemetry, ClassLoadingMXBean classBean) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);

    observables.add(
        meter
            .counterBuilder("process.runtime.jvm.classes.loaded")
            .setDescription("Number of classes loaded since JVM start")
            .setUnit("1")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getTotalLoadedClassCount())));

    observables.add(
        meter
            .counterBuilder("process.runtime.jvm.classes.unloaded")
            .setDescription("Number of classes unloaded since JVM start")
            .setUnit("1")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getUnloadedClassCount())));
    observables.add(
        meter
            .upDownCounterBuilder("process.runtime.jvm.classes.current_loaded")
            .setDescription("Number of classes currently loaded")
            .setUnit("1")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(classBean.getLoadedClassCount())));
  }

  public static void closeObservers() {
    JmxRuntimeMetricsUtil.closeObservers(observables);
  }

  private Classes() {}
}

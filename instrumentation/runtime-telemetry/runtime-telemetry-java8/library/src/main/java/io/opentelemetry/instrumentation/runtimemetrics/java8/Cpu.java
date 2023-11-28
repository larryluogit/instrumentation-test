/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.CpuMethods;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Registers measurements that generate metrics about CPU.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Cpu.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   jvm.cpu.time 20.42
 *   jvm.cpu.count 8
 *   jvm.cpu.recent_utilization 0.1
 * </pre>
 */
public final class Cpu {

  // Visible for testing
  static final Cpu INSTANCE = new Cpu();

  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  /** Register observers for java runtime CPU metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return INSTANCE.registerObservers(
        openTelemetry,
        Runtime.getRuntime()::availableProcessors,
        CpuMethods.processCpuTime(),
        CpuMethods.processCpuUtilization());
  }

  // Visible for testing
  List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry,
      IntSupplier availableProcessors,
      @Nullable Supplier<Long> processCpuTime,
      @Nullable Supplier<Double> processCpuUtilization) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();

    if (processCpuTime != null) {
      observables.add(
          meter
              .counterBuilder("jvm.cpu.time")
              .ofDoubles()
              .setDescription("CPU time used by the process as reported by the JVM.")
              .setUnit("s")
              .buildWithCallback(
                  observableMeasurement -> {
                    Long cpuTimeNanos = processCpuTime.get();
                    if (cpuTimeNanos != null && cpuTimeNanos >= 0) {
                      observableMeasurement.record(cpuTimeNanos / NANOS_PER_S);
                    }
                  }));
    }
    observables.add(
        meter
            .upDownCounterBuilder("jvm.cpu.count")
            .setDescription("Number of processors available to the Java virtual machine.")
            .setUnit("{cpu}")
            .buildWithCallback(
                observableMeasurement ->
                    observableMeasurement.record(availableProcessors.getAsInt())));
    if (processCpuUtilization != null) {
      observables.add(
          meter
              .gaugeBuilder("jvm.cpu.recent_utilization")
              .setDescription("Recent CPU utilization for the process as reported by the JVM.")
              .setUnit("1")
              .buildWithCallback(
                  observableMeasurement -> {
                    Double cpuUsage = processCpuUtilization.get();
                    if (cpuUsage != null && cpuUsage >= 0) {
                      observableMeasurement.record(cpuUsage);
                    }
                  }));
    }

    return observables;
  }

  private Cpu() {}
}

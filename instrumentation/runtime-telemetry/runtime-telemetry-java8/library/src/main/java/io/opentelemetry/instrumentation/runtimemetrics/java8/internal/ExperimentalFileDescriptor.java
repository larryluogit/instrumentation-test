/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Registers measurements that generate experimental metrics about file descriptor.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class ExperimentalFileDescriptor {
  /** Register observers for java runtime experimental file descriptor metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return registerObservers(
        openTelemetry,
        FileDescriptorMethods.openFileDescriptorCount(),
        FileDescriptorMethods.maxFileDescriptorCount());
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry,
      Supplier<Long> openFileDescriptorCount,
      Supplier<Long> maxFileDescriptorCount) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();

    if (openFileDescriptorCount != null) {
      observables.add(
          meter
              .gaugeBuilder("os.file.descriptor.open")
              .setDescription("number of open file descriptors")
              .setUnit("{file}")
              .buildWithCallback(
                  observableMeasurement -> {
                    Long openCount = openFileDescriptorCount.get();
                    if (openCount != null && openCount >= 0) {
                      observableMeasurement.record(openCount);
                    }
                  }));
    }

    if (maxFileDescriptorCount != null) {
      observables.add(
          meter
              .gaugeBuilder("os.file.descriptor.max")
              .setDescription("maximum number of file descriptors")
              .setUnit("{file}")
              .buildWithCallback(
                  observableMeasurement -> {
                    Long maxCount = maxFileDescriptorCount.get();
                    if (maxCount != null && maxCount >= 0) {
                      observableMeasurement.record(maxCount);
                    }
                  }));
    }

    return observables;
  }

  private ExperimentalFileDescriptor() {
  }
}

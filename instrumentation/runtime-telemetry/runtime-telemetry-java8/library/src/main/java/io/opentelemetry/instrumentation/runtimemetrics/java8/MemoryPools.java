/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import io.opentelemetry.semconv.JvmAttributes;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers measurements that generate metrics about JVM memory pools. The metrics generated by
 * this class follow <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">the
 * stable JVM metrics semantic conventions</a>.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * MemoryPools.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   jvm.memory.used{type="heap",pool="G1 Eden Space"} 2500000
 *   jvm.memory.committed{type="heap",pool="G1 Eden Space"} 3000000
 *   jvm.memory.limit{type="heap",pool="G1 Eden Space"} 4000000
 *   jvm.memory.used_after_last_gc{type="heap",pool="G1 Eden Space"} 1500000
 *   jvm.memory.used{type="non_heap",pool="Metaspace"} 400
 *   jvm.memory.committed{type="non_heap",pool="Metaspace"} 500
 * </pre>
 */
public final class MemoryPools {

  /** Register observers for java runtime memory metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return registerObservers(openTelemetry, ManagementFactory.getMemoryPoolMXBeans());
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry, List<MemoryPoolMXBean> poolBeans) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    List<AutoCloseable> observables = new ArrayList<>();

    observables.add(
        meter
            .upDownCounterBuilder("jvm.memory.used")
            .setDescription("Measure of memory used.")
            .setUnit("By")
            .buildWithCallback(
                callback(
                    JvmAttributes.JVM_MEMORY_POOL_NAME,
                    JvmAttributes.JVM_MEMORY_TYPE,
                    poolBeans,
                    MemoryPoolMXBean::getUsage,
                    MemoryUsage::getUsed)));
    observables.add(
        meter
            .upDownCounterBuilder("jvm.memory.committed")
            .setDescription("Measure of memory committed.")
            .setUnit("By")
            .buildWithCallback(
                callback(
                    JvmAttributes.JVM_MEMORY_POOL_NAME,
                    JvmAttributes.JVM_MEMORY_TYPE,
                    poolBeans,
                    MemoryPoolMXBean::getUsage,
                    MemoryUsage::getCommitted)));
    observables.add(
        meter
            .upDownCounterBuilder("jvm.memory.limit")
            .setDescription("Measure of max obtainable memory.")
            .setUnit("By")
            .buildWithCallback(
                callback(
                    JvmAttributes.JVM_MEMORY_POOL_NAME,
                    JvmAttributes.JVM_MEMORY_TYPE,
                    poolBeans,
                    MemoryPoolMXBean::getUsage,
                    MemoryUsage::getMax)));
    observables.add(
        meter
            .upDownCounterBuilder("jvm.memory.used_after_last_gc")
            .setDescription(
                "Measure of memory used, as measured after the most recent garbage collection event on this pool.")
            .setUnit("By")
            .buildWithCallback(
                callback(
                    JvmAttributes.JVM_MEMORY_POOL_NAME,
                    JvmAttributes.JVM_MEMORY_TYPE,
                    poolBeans,
                    MemoryPoolMXBean::getCollectionUsage,
                    MemoryUsage::getUsed)));

    return observables;
  }

  // Visible for testing
  static Consumer<ObservableLongMeasurement> callback(
      AttributeKey<String> poolNameKey,
      AttributeKey<String> memoryTypeKey,
      List<MemoryPoolMXBean> poolBeans,
      Function<MemoryPoolMXBean, MemoryUsage> memoryUsageExtractor,
      Function<MemoryUsage, Long> valueExtractor) {
    List<Attributes> attributeSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      attributeSets.add(
          Attributes.builder()
              .put(poolNameKey, pool.getName())
              .put(memoryTypeKey, memoryType(pool.getType()))
              .build());
    }

    return measurement -> {
      for (int i = 0; i < poolBeans.size(); i++) {
        Attributes attributes = attributeSets.get(i);
        MemoryUsage memoryUsage = memoryUsageExtractor.apply(poolBeans.get(i));
        if (memoryUsage == null) {
          // JVM may return null in special cases for MemoryPoolMXBean.getUsage() and
          // MemoryPoolMXBean.getCollectionUsage()
          continue;
        }
        long value = valueExtractor.apply(memoryUsage);
        if (value != -1) {
          measurement.record(value, attributes);
        }
      }
    };
  }

  private static String memoryType(MemoryType memoryType) {
    switch (memoryType) {
      case HEAP:
        return JvmAttributes.JvmMemoryTypeValues.HEAP;
      case NON_HEAP:
        return JvmAttributes.JvmMemoryTypeValues.NON_HEAP;
    }
    return "unknown";
  }

  private MemoryPools() {}
}

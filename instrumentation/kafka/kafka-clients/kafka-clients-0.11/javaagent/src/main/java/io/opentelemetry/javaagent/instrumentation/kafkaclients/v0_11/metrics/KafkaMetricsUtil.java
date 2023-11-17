/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetrySupplier;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;

public final class KafkaMetricsUtil {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";
  private static final boolean METRICS_ENABLED =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kafka.metric-reporter.enabled", true);

  @SuppressWarnings("unchecked")
  public static void enhanceConfig(Map<? super String, Object> config) {
    // skip enhancing configuration when metrics are disabled or when we have already enhanced it
    if (!METRICS_ENABLED
        || config.get(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME)
            != null) {
      return;
    }
    config.merge(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        OpenTelemetryMetricsReporter.class.getName(),
        (class1, class2) -> {
          // class1 is either a class name or List of class names or classes
          if (class1 instanceof List) {
            List<Object> result = new ArrayList<>();
            result.addAll((List<Object>) class1);
            result.add(class2);
            return result;
          } else if (class1 instanceof String) {
            String className1 = (String) class1;
            if (className1.isEmpty()) {
              return class2;
            }
          }
          return class1 + "," + class2;
        });
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(GlobalOpenTelemetry.get()));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        INSTRUMENTATION_NAME);
  }

  private KafkaMetricsUtil() {}
}

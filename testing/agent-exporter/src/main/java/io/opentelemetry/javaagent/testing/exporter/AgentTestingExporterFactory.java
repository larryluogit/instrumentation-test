/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AgentTestingExporterFactory {

  static final OtlpInMemoryCollector collector = new OtlpInMemoryCollector();

  static final OtlpInMemorySpanExporter spanExporter = new OtlpInMemorySpanExporter(collector);
  static final OtlpInMemoryMetricExporter metricExporter =
      new OtlpInMemoryMetricExporter(collector);
  static final OtlpInMemoryLogExporter logExporter = new OtlpInMemoryLogExporter(collector);

  public static List<byte[]> getSpanExportRequests() {
    AgentTestingCustomizer.spanProcessor.forceFlush().join(10, TimeUnit.SECONDS);
    return collector.getTraceExportRequests();
  }

  public static List<byte[]> getMetricExportRequests() {
    return collector.getMetricsExportRequests();
  }

  public static List<byte[]> getLogExportRequests() {
    AgentTestingLogsCustomizer.logProcessor.forceFlush().join(10, TimeUnit.SECONDS);
    return collector.getLogsExportRequests();
  }

  public static void reset() {
    // Finish any pending trace or log exports before resetting. There is no such thing as
    // "finishing" metrics so we don't flush it here.
    List<CompletableResultCode> results =
        Arrays.asList(
            AgentTestingLogsCustomizer.logProcessor.forceFlush(),
            AgentTestingCustomizer.spanProcessor.forceFlush());
    CompletableResultCode.ofAll(results).join(10, TimeUnit.SECONDS);
    collector.reset();
  }

  public static boolean forceFlushCalled() {
    return AgentTestingCustomizer.spanProcessor.forceFlushCalled;
  }
}

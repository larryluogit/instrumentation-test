/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter} and {@link
 * io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter}.
 *
 * <p>Get Exporter Service Name
 *
 * <p>Get Exporter Endpoint
 *
 * <p>Get max wait time for Collector to process Span Batches
 */
@ConfigurationProperties(prefix = "otel.exporter.otlp")
public final class OtlpExporterProperties {

  private boolean enabled = true;
  @Nullable private String endpoint;

  @Nullable private String protocol;

  private final Map<String, String> headers = new HashMap<>();

  @Nullable private Duration timeout;
  private final SignalProperties traces = new SignalProperties();
  private final SignalProperties metrics = new SignalProperties();
  private final SignalProperties logs = new SignalProperties();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Nullable
  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  @Nullable
  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(@Nullable String protocol) {
    this.protocol = protocol;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  @Nullable
  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public SignalProperties getTraces() {
    return traces;
  }

  public SignalProperties getMetrics() {
    return metrics;
  }

  public SignalProperties getLogs() {
    return logs;
  }

  public static class SignalProperties {

    private boolean enabled = true;
    @Nullable private String endpoint;

    @Nullable private String protocol;

    private final Map<String, String> headers = new HashMap<>();

    @Nullable private Duration timeout;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Nullable
    public String getEndpoint() {
      return endpoint;
    }

    public void setEndpoint(@Nullable String endpoint) {
      this.endpoint = endpoint;
    }

    @Nullable
    public String getProtocol() {
      return protocol;
    }

    public void setProtocol(@Nullable String protocol) {
      this.protocol = protocol;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    @Nullable
    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(@Nullable Duration timeout) {
      this.timeout = timeout;
    }
  }
}

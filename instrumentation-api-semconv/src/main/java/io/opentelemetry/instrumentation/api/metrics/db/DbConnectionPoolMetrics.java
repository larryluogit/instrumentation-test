/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.metrics.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.instrumentation.api.metrics.DurationHistogram;
import io.opentelemetry.instrumentation.api.metrics.DurationHistogramFactory;

/**
 * A helper class that models the <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/database-metrics.md#connection-pools">database
 * client connection pool metrics semantic conventions</a>.
 */
public final class DbConnectionPoolMetrics {

  static final AttributeKey<String> POOL_NAME = stringKey("pool.name");
  static final AttributeKey<String> CONNECTION_STATE = stringKey("state");

  static final String STATE_IDLE = "idle";
  static final String STATE_USED = "used";

  public static DbConnectionPoolMetrics create(
      OpenTelemetry openTelemetry, String instrumentationName, String poolName) {

    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return new DbConnectionPoolMetrics(meterBuilder.build(), Attributes.of(POOL_NAME, poolName));
  }

  private final Meter meter;
  private final Attributes attributes;
  private final Attributes usedConnectionsAttributes;
  private final Attributes idleConnectionsAttributes;

  DbConnectionPoolMetrics(Meter meter, Attributes attributes) {
    this.meter = meter;
    this.attributes = attributes;
    usedConnectionsAttributes = attributes.toBuilder().put(CONNECTION_STATE, STATE_USED).build();
    idleConnectionsAttributes = attributes.toBuilder().put(CONNECTION_STATE, STATE_IDLE).build();
  }

  public ObservableLongMeasurement connections() {
    return meter
        .upDownCounterBuilder("db.client.connections.usage")
        .setUnit("{connections}")
        .setDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .buildObserver();
  }

  public ObservableLongMeasurement minIdleConnections() {
    return meter
        .upDownCounterBuilder("db.client.connections.idle.min")
        .setUnit("{connections}")
        .setDescription("The minimum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxIdleConnections() {
    return meter
        .upDownCounterBuilder("db.client.connections.idle.max")
        .setUnit("{connections}")
        .setDescription("The maximum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxConnections() {
    return meter
        .upDownCounterBuilder("db.client.connections.max")
        .setUnit("{connections}")
        .setDescription("The maximum number of open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement pendingRequestsForConnection() {
    return meter
        .upDownCounterBuilder("db.client.connections.pending_requests")
        .setUnit("{requests}")
        .setDescription(
            "The number of pending requests for an open connection, cumulative for the entire pool.")
        .buildObserver();
  }

  public BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return meter.batchCallback(callback, observableMeasurement, additionalMeasurements);
  }

  public LongCounter connectionTimeouts() {
    return meter
        .counterBuilder("db.client.connections.timeouts")
        .setUnit("{timeouts}")
        .setDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .build();
  }

  public DurationHistogram connectionCreateTime() {
    return DurationHistogramFactory.create(
        meter, "db.client.connections.create_time", "The time it took to create a new connection.");
  }

  public DurationHistogram connectionWaitTime() {
    return DurationHistogramFactory.create(
        meter,
        "db.client.connections.wait_time",
        "The time it took to obtain an open connection from the pool.");
  }

  public DurationHistogram connectionUseTime() {
    return DurationHistogramFactory.create(
        meter,
        "db.client.connections.use_time",
        "The time between borrowing a connection and returning it to the pool.");
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public Attributes getUsedConnectionsAttributes() {
    return usedConnectionsAttributes;
  }

  public Attributes getIdleConnectionsAttributes() {
    return idleConnectionsAttributes;
  }
}

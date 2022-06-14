/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0;

import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import io.opentelemetry.api.OpenTelemetry;

public final class C3p0Telemetry {
  /** Returns a new {@link C3p0Telemetry} configured with the given {@link OpenTelemetry}. */
  public static C3p0Telemetry create(OpenTelemetry openTelemetry) {
    return new C3p0Telemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private C3p0Telemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Start collecting metrics for given connection pool. */
  public void registerMetrics(AbstractPoolBackedDataSource universalConnectionPool) {
    ConnectionPoolMetrics.registerMetrics(openTelemetry, universalConnectionPool);
  }

  /** Stop collecting metrics for given connection pool. */
  public void unregisterMetrics(AbstractPoolBackedDataSource universalConnectionPool) {
    ConnectionPoolMetrics.unregisterMetrics(universalConnectionPool);
  }
}

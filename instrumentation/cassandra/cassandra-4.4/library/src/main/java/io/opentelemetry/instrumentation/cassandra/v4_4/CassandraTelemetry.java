/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting cassandra sessions. */
public class CassandraTelemetry {

  /** Returns a new {@link CassandraTelemetry} configured with the given {@link OpenTelemetry}. */
  public static CassandraTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link CassandraTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static CassandraTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new CassandraTelemetryBuilder(openTelemetry);
  }

  private final TracingCqlSession tracingCqlSession;

  protected CassandraTelemetry(Instrumenter<CassandraRequest, ExecutionInfo> instrumenter) {
    this.tracingCqlSession = new TracingCqlSession(instrumenter);
  }

  /**
   * Construct a new tracing-enable CqlSession using the provided {@link CqlSession} instance.
   *
   * @param session An instance of CqlSession configured as desired.
   * @return a {@link TracingCqlSession}.
   */
  public CqlSession wrap(CqlSession session) {
    return tracingCqlSession.wrapSession(session);
  }
}

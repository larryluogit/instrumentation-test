/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class CassandraSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.cassandra-3.0";

  // could use RESPONSE "ResultSet" here, but using RESPONSE "ExecutionInfo" in cassandra-4.0
  // instrumentation (see comment over there for why), so also using here for consistency
  private static final Instrumenter<CassandraRequest, ExecutionInfo> INSTRUMENTER;

  static {
    DbAttributesExtractor<CassandraRequest, ExecutionInfo> attributesExtractor =
        new CassandraSqlAttributesExtractor();
    SpanNameExtractor<CassandraRequest> spanName = DbSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<CassandraRequest, ExecutionInfo>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(
                NetClientAttributesExtractor.create(new CassandraNetAttributesGetter()))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<CassandraRequest, ExecutionInfo> instrumenter() {
    return INSTRUMENTER;
  }

  private CassandraSingletons() {}
}

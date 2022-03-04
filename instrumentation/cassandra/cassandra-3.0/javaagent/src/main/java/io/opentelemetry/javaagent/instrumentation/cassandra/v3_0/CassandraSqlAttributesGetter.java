/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter implements SqlClientAttributesGetter<CassandraRequest> {

  @Override
  public String system(CassandraRequest request) {
    return SemanticAttributes.DbSystemValues.CASSANDRA;
  }

  @Override
  @Nullable
  public String user(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String name(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Override
  @Nullable
  public String connectionString(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String rawStatement(CassandraRequest request) {
    return request.getStatement();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import javax.annotation.Nullable;

final class CassandraSqlAttributesGetter implements SqlClientAttributesGetter<CassandraRequest> {

  @Override
  public String getSystem(CassandraRequest request) {
    return DbIncubatingAttributes.DbSystemValues.CASSANDRA;
  }

  @Override
  @Nullable
  public String getUser(CassandraRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getName(CassandraRequest request) {
    return request.getSession().getLoggedKeyspace();
  }

  @Override
  @Nullable
  public String getRawStatement(CassandraRequest request) {
    return request.getStatement();
  }
}

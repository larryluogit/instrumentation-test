/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesOnEndExtractor;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraNetAttributesExtractor
    extends InetSocketAddressNetAttributesOnEndExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  @Nullable
  public String transport(CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(
      CassandraRequest request, @Nullable ExecutionInfo executionInfo) {
    return executionInfo == null ? null : executionInfo.getQueriedHost().getSocketAddress();
  }
}

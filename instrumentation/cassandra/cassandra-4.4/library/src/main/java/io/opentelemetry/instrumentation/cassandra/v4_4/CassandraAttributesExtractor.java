/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  private static final Logger logger =
      Logger.getLogger(CassandraAttributesExtractor.class.getName());

  private static final Field proxyAddressField = getProxyAddressField();

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CassandraRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CassandraRequest request,
      @Nullable ExecutionInfo executionInfo,
      @Nullable Throwable error) {
    if (executionInfo == null) {
      return;
    }

    Node coordinator = executionInfo.getCoordinator();
    if (coordinator != null) {
      updateServerAddressAndPort(attributes, coordinator);

      if (coordinator.getDatacenter() != null) {
        attributes.put(
            DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      if (coordinator.getHostId() != null) {
        attributes.put(
            DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID, coordinator.getHostId().toString());
      }
    }
    attributes.put(
        DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT,
        executionInfo.getSpeculativeExecutionCount());

    Statement<?> statement = (Statement<?>) executionInfo.getRequest();
    String consistencyLevel;
    DriverExecutionProfile config =
        request.getSession().getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      consistencyLevel = statement.getConsistencyLevel().name();
    } else {
      consistencyLevel = config.getString(DefaultDriverOption.REQUEST_CONSISTENCY);
    }
    attributes.put(DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);

    if (statement.getPageSize() > 0) {
      attributes.put(DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE, statement.getPageSize());
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        attributes.put(DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE, pageSize);
      }
    }

    Boolean idempotent = statement.isIdempotent();
    if (idempotent == null) {
      idempotent = config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    }
    attributes.put(DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE, idempotent);
  }

  private static void updateServerAddressAndPort(AttributesBuilder attributes, Node coordinator) {
    EndPoint endPoint = coordinator.getEndPoint();
    if (endPoint instanceof DefaultEndPoint) {
      InetSocketAddress address = ((DefaultEndPoint) endPoint).resolve();
      attributes.put(ServerAttributes.SERVER_ADDRESS, address.getHostString());
      attributes.put(ServerAttributes.SERVER_PORT, address.getPort());
    } else if (endPoint instanceof SniEndPoint && proxyAddressField != null) {
      SniEndPoint sniEndPoint = (SniEndPoint) endPoint;
      Object object = null;
      try {
        object = proxyAddressField.get(sniEndPoint);
      } catch (Exception e) {
        logger.log(
            Level.FINE,
            "Error when accessing the private field proxyAddress of SniEndPoint using reflection.",
            e);
      }
      if (object instanceof InetSocketAddress) {
        InetSocketAddress address = (InetSocketAddress) object;
        attributes.put(ServerAttributes.SERVER_ADDRESS, address.getHostString());
        attributes.put(ServerAttributes.SERVER_PORT, address.getPort());
      }
    }
  }

  @Nullable
  private static Field getProxyAddressField() {
    try {
      Field field = SniEndPoint.class.getDeclaredField("proxyAddress");
      field.setAccessible(true);
      return field;
    } catch (Exception e) {
      return null;
    }
  }
}

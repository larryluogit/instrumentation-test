/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.instrumentation.api.db.QueryNormalizationConfig.isQueryNormalizationEnabled;

import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.javaagent.instrumentation.api.db.sanitizer.ParseException;
import io.opentelemetry.javaagent.instrumentation.api.db.sanitizer.SqlSanitizer;
import io.opentelemetry.javaagent.instrumentation.api.db.sanitizer.SqlStatementInfoExtractor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JdbcUtils {

  private static final Logger log = LoggerFactory.getLogger(JdbcUtils.class);

  private static final boolean NORMALIZATION_ENABLED = isQueryNormalizationEnabled("jdbc");

  private static Field c3poField = null;

  /** Returns the unwrapped connection or null if exception was thrown. */
  public static Connection connectionFromStatement(Statement statement) {
    Connection connection;
    try {
      connection = statement.getConnection();

      if (c3poField != null) {
        if (connection.getClass().getName().equals("com.mchange.v2.c3p0.impl.NewProxyConnection")) {
          return (Connection) c3poField.get(connection);
        }
      }

      try {
        // unwrap the connection to cache the underlying actual connection and to not cache proxy
        // objects
        if (connection.isWrapperFor(Connection.class)) {
          connection = connection.unwrap(Connection.class);
        }
      } catch (Exception | AbstractMethodError e) {
        if (connection != null) {
          // Attempt to work around c3po delegating to an connection that doesn't support
          // unwrapping.
          Class<? extends Connection> connectionClass = connection.getClass();
          if (connectionClass.getName().equals("com.mchange.v2.c3p0.impl.NewProxyConnection")) {
            Field inner = connectionClass.getDeclaredField("inner");
            inner.setAccessible(true);
            c3poField = inner;
            return (Connection) c3poField.get(connection);
          }
        }

        // perhaps wrapping isn't supported?
        // ex: org.h2.jdbc.JdbcConnection v1.3.175
        // or: jdts.jdbc which always throws `AbstractMethodError` (at least up to version 1.3)
        // Stick with original connection.
      }
    } catch (Throwable e) {
      // Had some problem getting the connection.
      log.debug("Could not get connection for StatementAdvice", e);
      return null;
    }
    return connection;
  }

  /** Returns null if the sql could not be sanitized for any reason. */
  public static String sanitizeSql(String sql) {
    if (!NORMALIZATION_ENABLED) {
      return sql;
    }
    try {
      return SqlSanitizer.sanitize(sql);
    } catch (Exception e) {
      log.debug("Could not sanitize sql", e);
      return null;
    }
  }

  public static SqlStatementInfo sanitizeAndExtractInfo(String sql) {
    try {
      return SqlStatementInfoExtractor.extract(sanitizeSql(sql));
    } catch (ParseException e) {
      log.debug("Could not extract sql info", e);
      return new SqlStatementInfo(null, null, null);
    }
  }
}

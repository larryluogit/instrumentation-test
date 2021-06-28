/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/** JDBC driver for OpenTelemetry. */
public final class OpenTelemetryDriver implements Driver {

  // visible for testing
  static final OpenTelemetryDriver INSTANCE = new OpenTelemetryDriver();

  private static final String INTERCEPTOR_MODE_URL_PREFIX = "jdbc:otel:";
  private static final AtomicBoolean REGISTERED = new AtomicBoolean();

  private static volatile boolean interceptorMode = false;

  static {
    try {
      register();
    } catch (SQLException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Ensure {@code TracingDriver} be the first driver of {@link DriverManager} to make sure
   * "interceptor mode" works. WARNING: Driver like Oracle JDBC may fail since it's destroyed
   * forever after deregistration.
   */
  public static synchronized void ensureRegisteredAsTheFirstDriver() {
    try {
      Enumeration<Driver> enumeration = DriverManager.getDrivers();
      List<Driver> drivers = new ArrayList<>();
      for (int i = 0; enumeration.hasMoreElements(); ++i) {
        Driver driver = enumeration.nextElement();
        if (i == 0 && driver == INSTANCE) {
          // the first driver is OTEL driver, skip all this verification
          return;
        }
        if (!(driver instanceof OpenTelemetryDriver)) {
          drivers.add(driver);
        }
        DriverManager.deregisterDriver(driver);
      }

      // register OTEL driver first
      register();

      // register other drivers
      for (Driver driver : drivers) {
        DriverManager.registerDriver(driver);
      }
    } catch (SQLException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * Turns "interceptor mode" on or off.
   *
   * @param interceptorMode The {@code interceptorMode} value.
   */
  public static void setInterceptorMode(final boolean interceptorMode) {
    OpenTelemetryDriver.interceptorMode = interceptorMode;
  }

  /**
   * Register the driver against {@link DriverManager}. This is done automatically when the class is
   * loaded. Dropping the driver from DriverManager's list is possible using {@link #deregister()}
   * method.
   *
   * @throws IllegalStateException if the driver is already registered
   * @throws SQLException if registering the driver fails
   */
  public static void register() throws SQLException {
    if (!REGISTERED.compareAndSet(false, true)) {
      throw new IllegalStateException(
          "Driver is already registered. It can only be registered once.");
    }
    DriverManager.registerDriver(INSTANCE);
  }

  /**
   * According to JDBC specification, this driver is registered against {@link DriverManager} when
   * the class is loaded. To avoid leaks, this method allow unregistering the driver so that the
   * class can be gc'ed if necessary.
   *
   * @throws IllegalStateException if the driver is not registered
   * @throws SQLException if deregistering the driver fails
   */
  public static void deregister() throws SQLException {
    if (!REGISTERED.compareAndSet(true, false)) {
      throw new IllegalStateException(
          "Driver is not registered (or it has not been registered using Driver.register() method)");
    }
    DriverManager.deregisterDriver(INSTANCE);
  }

  /** Returns {@code true} if the driver is registered against {@link DriverManager}. */
  public static boolean isRegistered() {
    return REGISTERED.get();
  }

  private static Driver findDriver(String realUrl) {
    for (Driver candidate : Collections.list(DriverManager.getDrivers())) {
      try {
        if (!(candidate instanceof OpenTelemetryDriver) && candidate.acceptsURL(realUrl)) {
          return candidate;
        }
      } catch (SQLException ignored) {
        // intentionally ignore exception
      }
    }

    throw new IllegalStateException("Unable to find a driver that accepts url: " + realUrl);
  }

  /**
   * Parses out the real JDBC connection URL by removing "otel:" prefix.
   *
   * @param url the connection URL
   * @return the parsed URL
   */
  private static String extractRealUrl(String url) {
    return url.startsWith(INTERCEPTOR_MODE_URL_PREFIX)
        ? url.replace(INTERCEPTOR_MODE_URL_PREFIX, "jdbc:")
        : url;
  }

  @Nullable
  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("url is required");
    }

    if (!acceptsURL(url)) {
      return null;
    }

    final String realUrl = extractRealUrl(url);

    // find the real driver for the URL
    final Driver wrappedDriver = findDriver(realUrl);

    final Connection connection = wrappedDriver.connect(realUrl, info);

    final DbInfo dbInfo = JdbcConnectionUrlParser.parse(realUrl, info);

    return new OpenTelemetryConnection(connection, dbInfo);
  }

  @Override
  public boolean acceptsURL(String url) {
    if (url == null) {
      return false;
    }
    if (url.startsWith(INTERCEPTOR_MODE_URL_PREFIX)) {
      return true;
    }
    return interceptorMode && url.startsWith("jdbc:") && url.length() > 5;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    if (url == null || url.trim().isEmpty()) {
      throw new IllegalArgumentException("url is required");
    }

    final String realUrl = extractRealUrl(url);
    final Driver wrappedDriver = findDriver(realUrl);
    return wrappedDriver.getPropertyInfo(realUrl, info);
  }

  @Override
  public int getMajorVersion() {
    return 1;
  }

  @Override
  public int getMinorVersion() {
    return 4;
  }

  /** Returns {@literal false} because not all delegated drivers are JDBC compliant. */
  @Override
  public boolean jdbcCompliant() {
    return false;
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    throw new SQLFeatureNotSupportedException("Feature not supported");
  }
}

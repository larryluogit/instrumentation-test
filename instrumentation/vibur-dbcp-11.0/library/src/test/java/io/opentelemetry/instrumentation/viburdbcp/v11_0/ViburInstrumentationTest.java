/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp.v11_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.instrumentation.viburdbcp.AbstractViburInstrumentationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.vibur.dbcp.ViburDBCPDataSource;

class ViburInstrumentationTest extends AbstractViburInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static ViburTelemetry telemetry;

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @BeforeAll
  static void setup() {
    telemetry = ViburTelemetry.create(testing.getOpenTelemetry());
  }

  @Override
  protected void configure(ViburDBCPDataSource viburDataSource) {
    telemetry.registerMetrics(viburDataSource);
  }

  @Override
  protected void shutdown(ViburDBCPDataSource viburDataSource) {
    telemetry.unregisterMetrics(viburDataSource);
  }
}

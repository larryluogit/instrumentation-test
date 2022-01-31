/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import io.opentelemetry.instrumentation.oshi.AbstractSystemMetricsTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SystemMetricsTest extends AbstractSystemMetricsTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected void registerMetrics() {}

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}

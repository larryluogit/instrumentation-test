/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quartz.v2_0;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.Scheduler;

class QuartzTest extends AbstractQuartzTest {

  @RegisterExtension InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected void configureScheduler(Scheduler scheduler) {
    QuartzTelemetry.builder(testing.getOpenTelemetry())
        // TODO run tests both with and without experimental span attributes
        .setCaptureExperimentalSpanAttributes(true)
        .build()
        .configure(scheduler);
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }
}

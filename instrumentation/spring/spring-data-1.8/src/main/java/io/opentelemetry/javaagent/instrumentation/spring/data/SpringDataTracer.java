/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public final class SpringDataTracer extends BaseTracer {
  public static final SpringDataTracer TRACER = new SpringDataTracer();

  private SpringDataTracer() {}

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spring-data";
  }
}

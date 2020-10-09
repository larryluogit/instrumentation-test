/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.servlet.http;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HttpServletTracer extends BaseTracer {
  public static final HttpServletTracer TRACER = new HttpServletTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet";
  }
}

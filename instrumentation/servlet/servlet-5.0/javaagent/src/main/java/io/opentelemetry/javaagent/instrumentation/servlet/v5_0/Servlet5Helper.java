/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import static io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons.instrumenter;

import io.opentelemetry.javaagent.instrumentation.servlet.ServletHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Servlet5Helper extends ServletHelper<HttpServletRequest, HttpServletResponse> {
  private static final Servlet5Helper HELPER = new Servlet5Helper();

  public static Servlet5Helper helper() {
    return HELPER;
  }

  private Servlet5Helper() {
    super(instrumenter(), Servlet5Accessor.INSTANCE);
  }
}

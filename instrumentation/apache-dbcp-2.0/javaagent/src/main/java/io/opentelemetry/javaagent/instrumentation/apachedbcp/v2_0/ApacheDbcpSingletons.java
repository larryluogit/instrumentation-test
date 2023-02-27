/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;

public final class ApacheDbcpSingletons {

  private static final ApacheDbcpTelemetry apacheDbcpTelemetry =
      ApacheDbcpTelemetry.create(GlobalOpenTelemetry.get());

  public static ApacheDbcpTelemetry telemetry() {
    return apacheDbcpTelemetry;
  }

  private ApacheDbcpSingletons() {}
}

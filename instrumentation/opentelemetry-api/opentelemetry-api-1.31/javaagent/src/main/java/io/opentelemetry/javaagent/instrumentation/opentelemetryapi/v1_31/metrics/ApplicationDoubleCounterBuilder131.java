/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleCounterBuilder;
import java.util.List;

final class ApplicationDoubleCounterBuilder131 extends ApplicationDoubleCounterBuilder
    implements ExtendedDoubleCounterBuilder {

  private final io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder;

  ApplicationDoubleCounterBuilder131(
      io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public ExtendedDoubleCounterBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.extension.incubator.metrics.ExtendedDoubleCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}

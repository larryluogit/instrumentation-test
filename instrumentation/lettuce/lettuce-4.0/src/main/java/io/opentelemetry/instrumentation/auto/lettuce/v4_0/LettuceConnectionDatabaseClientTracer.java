/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v4_0;

public class LettuceConnectionDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<String> {

  public static final LettuceConnectionDatabaseClientTracer TRACER =
      new LettuceConnectionDatabaseClientTracer();

  @Override
  protected String normalizeQuery(String command) {
    return command;
  }
}

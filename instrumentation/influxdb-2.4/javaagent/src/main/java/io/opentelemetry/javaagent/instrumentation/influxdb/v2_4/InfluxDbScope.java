/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

import static io.opentelemetry.javaagent.instrumentation.influxdb.v2_4.InfluxDbSingletons.instrumenter;

/** Container used to carry state between enter and exit advices */
public class InfluxDbScope {
  private final InfluxDbRequest influxDbRequest;
  private final Context context;
  private final Scope scope;

  private InfluxDbScope(InfluxDbRequest influxDbRequest, Context context, Scope scope) {
    this.influxDbRequest = influxDbRequest;
    this.context = context;
    this.scope = scope;
  }

  public static InfluxDbScope start(
      Instrumenter<InfluxDbRequest, Void> instrumenter,
      Context parentContext,
      InfluxDbRequest influxDbRequest) {
    Context context = instrumenter.start(parentContext, influxDbRequest);
    return new InfluxDbScope(influxDbRequest, context, context.makeCurrent());
  }

  public void end(Throwable throwable) {
    if (scope == null) {
      return;
    }

    scope.close();

    instrumenter().end(context, influxDbRequest, null, throwable);
  }
}
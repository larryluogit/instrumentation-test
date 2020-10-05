/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.OpenTelemetry.getPropagators;

import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

public abstract class RpcServerTracer<REQUEST> extends BaseTracer {

  protected abstract TextMapPropagator.Getter<REQUEST> getGetter();

  protected <C> Context extract(C carrier, TextMapPropagator.Getter<C> getter) {
    // TODO add context leak debug

    // Using Context.ROOT here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    return getPropagators().getTextMapPropagator().extract(Context.ROOT, carrier, getter);
  }
}

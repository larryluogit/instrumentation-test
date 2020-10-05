/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.grizzly;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.glassfish.grizzly.http.HttpRequestPacket;

public class ExtractAdapter implements TextMapPropagator.Getter<HttpRequestPacket> {
  public static final ExtractAdapter GETTER = new ExtractAdapter();

  @Override
  public String get(HttpRequestPacket request, String key) {
    return request.getHeader(key);
  }
}

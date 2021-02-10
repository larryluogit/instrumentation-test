/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.extension.trace.propagation.AwsXrayPropagator;
import java.util.Collections;
import java.util.Map;
import org.apache.camel.Endpoint;

final class CamelPropagationUtil {

  private CamelPropagationUtil() {}

  private static boolean isAwsPropagated(Endpoint endpoint) {
    return endpoint.getClass().getName().endsWith("SqsEndpoint");
  }

  static Context extractParent(final Map<String, Object> exchangeHeaders, Endpoint endpoint) {
    return (isAwsPropagated(endpoint)
        ? extractAwsPropagationParent(exchangeHeaders)
        : extractHttpPropagationParent(exchangeHeaders));
  }

  private static Context extractAwsPropagationParent(final Map<String, Object> exchangeHeaders) {
    return AwsXrayPropagator.getInstance()
        .extract(
            Context.current(),
            Collections.singletonMap("X-Amzn-Trace-Id", exchangeHeaders.get("AWSTraceHeader")),
            MapGetter.INSTANCE);
  }

  private static Context extractHttpPropagationParent(final Map<String, Object> exchangeHeaders) {
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.current(), exchangeHeaders, MapGetter.INSTANCE);
  }

  static void injectParent(Context context, final Map<String, Object> exchangeHeaders) {
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, exchangeHeaders, MapSetter.INSTANCE);
  }

  private static class MapGetter implements Getter<Map<String, Object>> {

    private static final MapGetter INSTANCE = new MapGetter();

    @Override
    public Iterable<String> keys(Map<String, Object> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, Object> map, String s) {
      return (map.containsKey(s) ? map.get(s).toString() : null);
    }
  }

  private static class MapSetter implements Setter<Map<String, Object>> {

    private static final MapSetter INSTANCE = new MapSetter();

    @Override
    public void set(Map<String, Object> carrier, String key, String value) {
      // Camel keys are internal ones
      if (!key.startsWith("Camel")) {
        carrier.put(key, value);
      }
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(io.opentelemetry.javaagent.tooling.InstrumentationModule.class)
public class AsyncHttpClientInstrumentationModule
    extends io.opentelemetry.javaagent.tooling.InstrumentationModule {
  public AsyncHttpClientInstrumentationModule() {
    super("async-http-client", "async-http-client-1.9");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RequestInstrumentation(), new ResponseInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.ning.http.client.AsyncHandler", Pair.class.getName());
  }
}

/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.context.propagation;

import application.io.grpc.Context;
import application.io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ApplicationTextMapPropagator implements TextMapPropagator {

  private static final Logger log = LoggerFactory.getLogger(ApplicationTextMapPropagator.class);

  private final io.opentelemetry.context.propagation.TextMapPropagator agentTextMapPropagator;
  private final ContextStore<Context, io.grpc.Context> contextStore;

  ApplicationTextMapPropagator(
      io.opentelemetry.context.propagation.TextMapPropagator agentTextMapPropagator,
      ContextStore<Context, io.grpc.Context> contextStore) {
    this.agentTextMapPropagator = agentTextMapPropagator;
    this.contextStore = contextStore;
  }

  @Override
  public List<String> fields() {
    return agentTextMapPropagator.fields();
  }

  @Override
  public <C> Context extract(
      Context applicationContext, C carrier, TextMapPropagator.Getter<C> applicationGetter) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return applicationContext;
    }
    io.grpc.Context agentUpdatedContext =
        agentTextMapPropagator.extract(agentContext, carrier, new AgentGetter<>(applicationGetter));
    if (agentUpdatedContext == agentContext) {
      return applicationContext;
    }
    contextStore.put(applicationContext, agentUpdatedContext);
    return applicationContext;
  }

  @Override
  public <C> void inject(
      Context applicationContext, C carrier, TextMapPropagator.Setter<C> applicationSetter) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return;
    }
    agentTextMapPropagator.inject(agentContext, carrier, new AgentSetter<>(applicationSetter));
  }

  private static class AgentGetter<C>
      implements io.opentelemetry.context.propagation.TextMapPropagator.Getter<C> {

    private final TextMapPropagator.Getter<C> applicationGetter;

    AgentGetter(TextMapPropagator.Getter<C> applicationGetter) {
      this.applicationGetter = applicationGetter;
    }

    @Override
    public String get(C carrier, String key) {
      return applicationGetter.get(carrier, key);
    }
  }

  private static class AgentSetter<C>
      implements io.opentelemetry.context.propagation.TextMapPropagator.Setter<C> {

    private final TextMapPropagator.Setter<C> applicationSetter;

    AgentSetter(Setter<C> applicationSetter) {
      this.applicationSetter = applicationSetter;
    }

    @Override
    public void set(C carrier, String key, String value) {
      applicationSetter.set(carrier, key, value);
    }
  }
}

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

package io.opentelemetry.instrumentation.auto.rmi.context;

import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ContextPayload wraps context information shared between client and server */
public class ContextPayload {

  private static final Logger log = LoggerFactory.getLogger(ContextPayload.class);

  public static final Tracer TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto.rmi");

  private final Map<String, String> context;
  public static final ExtractAdapter GETTER = new ExtractAdapter();
  public static final InjectAdapter SETTER = new InjectAdapter();

  public ContextPayload() {
    context = new HashMap<>();
  }

  public ContextPayload(Map<String, String> context) {
    this.context = context;
  }

  public static ContextPayload from(Span span) {
    ContextPayload payload = new ContextPayload();
    Context context = withSpan(span, Context.current());
    OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, payload, SETTER);
    return payload;
  }

  public static ContextPayload read(ObjectInput oi) throws IOException {
    try {
      Object object = oi.readObject();
      if (object instanceof Map) {
        return new ContextPayload((Map<String, String>) object);
      }
    } catch (ClassCastException | ClassNotFoundException ex) {
      log.debug("Error reading object", ex);
    }

    return null;
  }

  public Map<String, String> getContext() {
    return context;
  }

  public void write(ObjectOutput out) throws IOException {
    out.writeObject(context);
  }

  public static class ExtractAdapter implements TextMapPropagator.Getter<ContextPayload> {
    @Override
    public String get(ContextPayload carrier, String key) {
      return carrier.getContext().get(key);
    }
  }

  public static class InjectAdapter implements TextMapPropagator.Setter<ContextPayload> {
    @Override
    public void set(ContextPayload carrier, String key, String value) {
      carrier.getContext().put(key, value);
    }
  }
}

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

package io.opentelemetry.instrumentation.armeria.v1_0.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ArmeriaClientTracer
    extends HttpClientTracer<ClientRequestContext, ClientRequestContext, RequestLog> {

  ArmeriaClientTracer() {}

  ArmeriaClientTracer(Tracer tracer) {
    super(tracer);
  }

  @Override
  protected String method(ClientRequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  protected @Nullable String flavor(ClientRequestContext clientRequestContext) {
    return clientRequestContext.sessionProtocol().toString();
  }

  @Override
  @Nullable
  protected URI url(ClientRequestContext ctx) throws URISyntaxException {
    HttpRequest request = ctx.request();
    return request != null ? request.uri() : null;
  }

  @Override
  protected Integer status(RequestLog log) {
    return log.responseHeaders().status().code();
  }

  @Override
  @Nullable
  protected String requestHeader(ClientRequestContext ctx, String name) {
    HttpRequest request = ctx.request();
    return request != null ? request.headers().get(name) : null;
  }

  @Override
  @Nullable
  protected String responseHeader(RequestLog log, String name) {
    return log.responseHeaders().get(name);
  }

  @Override
  protected Setter<ClientRequestContext> getSetter() {
    return ArmeriaSetter.INSTANCE;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria-1.0";
  }

  private static class ArmeriaSetter implements Setter<ClientRequestContext> {

    private static final ArmeriaSetter INSTANCE = new ArmeriaSetter();

    @Override
    public void set(@Nullable ClientRequestContext ctx, String key, String value) {
      if (ctx != null) {
        ctx.addAdditionalRequestHeader(key, value);
      }
    }
  }
}

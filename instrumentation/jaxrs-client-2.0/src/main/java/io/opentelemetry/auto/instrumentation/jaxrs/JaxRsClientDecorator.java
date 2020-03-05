/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.jaxrs;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;

public class JaxRsClientDecorator
    extends HttpClientDecorator<ClientRequestContext, ClientResponseContext> {
  public static final JaxRsClientDecorator DECORATE = new JaxRsClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory()
          .get("io.opentelemetry.instrumentation.auto.jaxrs-client-2.0");

  @Override
  protected String getComponentName() {
    return "jax-rs.client";
  }

  @Override
  protected String method(final ClientRequestContext httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected URI url(final ClientRequestContext httpRequest) {
    return httpRequest.getUri();
  }

  @Override
  protected String hostname(final ClientRequestContext httpRequest) {
    return httpRequest.getUri().getHost();
  }

  @Override
  protected Integer port(final ClientRequestContext httpRequest) {
    return httpRequest.getUri().getPort();
  }

  @Override
  protected Integer status(final ClientResponseContext httpResponse) {
    return httpResponse.getStatus();
  }
}

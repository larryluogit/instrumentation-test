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

package io.opentelemetry.instrumentation.auto.jetty;

import io.opentelemetry.instrumentation.auto.servlet.v3.Servlet3HttpServerTracer;

public class Jetty91HttpServerTracer extends Servlet3HttpServerTracer {
  public static final Jetty91HttpServerTracer TRACER = new Jetty91HttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jetty-9.1";
  }
}

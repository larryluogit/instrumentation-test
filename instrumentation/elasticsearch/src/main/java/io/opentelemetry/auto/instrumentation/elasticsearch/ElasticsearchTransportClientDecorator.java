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
package io.opentelemetry.auto.instrumentation.elasticsearch;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class ElasticsearchTransportClientDecorator extends DatabaseClientDecorator {
  public static final ElasticsearchTransportClientDecorator DECORATE =
      new ElasticsearchTransportClientDecorator();

  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  @Override
  protected String service() {
    return "elasticsearch";
  }

  @Override
  protected String getComponentName() {
    return "elasticsearch-java";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.ELASTICSEARCH;
  }

  @Override
  protected String dbType() {
    return "elasticsearch";
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }

  public Span onRequest(final Span span, final Class action, final Class request) {
    if (action != null) {
      span.setAttribute(MoreTags.RESOURCE_NAME, action.getSimpleName());
      span.setAttribute("elasticsearch.action", action.getSimpleName());
    }
    if (request != null) {
      span.setAttribute("elasticsearch.request", request.getSimpleName());
    }
    return span;
  }
}

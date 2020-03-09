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
package io.opentelemetry.auto.instrumentation.hibernate;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.OrmClientDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HibernateDecorator extends OrmClientDecorator {
  public static final HibernateDecorator DECORATOR = new HibernateDecorator();
  // TODO use tracer names *.hibernate-3.3, *.hibernate-4.0, *.hibernate-4.3 respectively in each
  // module
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.hibernate");

  @Override
  protected String service() {
    return "hibernate";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.HIBERNATE;
  }

  @Override
  protected String getComponentName() {
    return "java-hibernate";
  }

  @Override
  protected String dbType() {
    return null;
  }

  @Override
  protected String dbUser(final Object o) {
    return null;
  }

  @Override
  protected String dbInstance(final Object o) {
    return null;
  }

  @Override
  public String entityName(final Object entity) {
    if (entity == null) {
      return null;
    }
    String name = null;
    final Set<String> annotations = new HashSet<>();
    for (final Annotation annotation : entity.getClass().getDeclaredAnnotations()) {
      annotations.add(annotation.annotationType().getName());
    }

    if (entity instanceof String) {
      // We were given an entity name, not the entity itself.
      name = (String) entity;
    } else if (annotations.contains("javax.persistence.Entity")) {
      // We were given an instance of an entity.
      name = entity.getClass().getName();
    } else if (entity instanceof List && ((List) entity).size() > 0) {
      // We have a list of entities.
      name = entityName(((List) entity).get(0));
    }

    return name;
  }
}

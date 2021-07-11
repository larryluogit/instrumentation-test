/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.extension.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.api.annotation.support.AttributeBindings;
import io.opentelemetry.instrumentation.api.annotation.support.BaseAttributeBinder;
import io.opentelemetry.instrumentation.api.caching.Cache;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;

class WithSpanAttributeBinder extends BaseAttributeBinder {

  private static final Cache<Method, AttributeBindings> bindings =
      Cache.newBuilder().setWeakKeys().build();

  @Override
  public AttributeBindings bind(Method method) {
    return bindings.computeIfAbsent(method, super::bind);
  }

  @Override
  protected @Nullable String[] attributeNamesForParameters(Method method, Parameter[] parameters) {
    String[] attributeNames = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      attributeNames[i] = attributeName(parameters[i]);
    }
    return attributeNames;
  }

  @Nullable
  private static String attributeName(Parameter parameter) {
    SpanAttribute annotation = parameter.getDeclaredAnnotation(SpanAttribute.class);
    if (annotation == null) {
      return null;
    }
    String value = annotation.value();
    if (!value.isEmpty()) {
      return value;
    } else if (parameter.isNamePresent()) {
      return parameter.getName();
    } else {
      return null;
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.servlet.MappingResolver;

public class ServletRequestContext<T> {
  private final T request;
  private final MappingResolver mappingResolver;

  public ServletRequestContext(T request) {
    this(request, null);
  }

  public ServletRequestContext(T request, MappingResolver mappingResolver) {
    this.request = request;
    this.mappingResolver = mappingResolver;
  }

  public T request() {
    return request;
  }

  public MappingResolver mappingResolver() {
    return mappingResolver;
  }
}

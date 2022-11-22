/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.server;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class HandlerCodeAttributesGetter implements CodeAttributesGetter<Object> {
  @Nullable
  @Override
  public Class<?> codeClass(Object handler) {
    return handler.getClass();
  }

  @Nullable
  @Override
  public String methodName(Object handler) {
    return "handle";
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import io.opentelemetry.javaagent.extension.matcher.internal.DelegatingMatcher;
import io.opentelemetry.javaagent.tooling.DefineClassHandler;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A matcher wrapper that skips matching and returns {@code false} when it is known that loading the
 * matched type will fail. If we know that the class that is currently loading can't be loaded
 * successfully we can skip transforming it.
 */
public class IgnoreFailedTypeMatcher implements ElementMatcher<TypeDescription>, DelegatingMatcher {
  private final ElementMatcher<TypeDescription> delegate;

  public IgnoreFailedTypeMatcher(ElementMatcher<TypeDescription> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean matches(TypeDescription target) {
    return !DefineClassHandler.isFailedClass(target.getTypeName()) && delegate.matches(target);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public ElementMatcher<?> getDelegate() {
    return delegate;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import javax.annotation.Nullable;

final class MapBackedCache<K, V> implements Cache<K, V> {

  private final ConcurrentMap<K, V> delegate;

  MapBackedCache(ConcurrentMap<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    return delegate.computeIfAbsent(key, mappingFunction);
  }

  @Nullable
  @Override
  public V get(K key) {
    return delegate.get(key);
  }

  @Override
  public void put(K key, V value) {
    delegate.put(key, value);
  }

  @Override
  public void remove(K key) {
    delegate.remove(key);
  }

  // Visible for tests
  int size() {
    return delegate.size();
  }
}

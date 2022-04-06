/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal.cache;

import io.opentelemetry.instrumentation.api.internal.cache.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A cache from keys to values.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface Cache<K, V> {

  /**
   * Returns new unbounded cache
   *
   * <p>Keys are referenced weakly and compared using identity comparison, not {@link
   * Object#equals(Object)}.
   */
  static <K, V> Cache<K, V> weak() {
    return new WeakLockFreeCache<>();
  }

  /**
   * Returns new bounded cache.
   *
   * <p>Both keys and values are strongly referenced.
   */
  static <K, V> Cache<K, V> bounded(int capacity) {
    ConcurrentLinkedHashMap<K, V> map =
        new ConcurrentLinkedHashMap.Builder<K, V>().maximumWeightedCapacity(capacity).build();
    return new MapBackedCache<>(map);
  }

  /**
   * Returns the cached value associated with the provided {@code key}. If no value is cached yet,
   * computes the value using {@code mappingFunction}, stores the result, and returns it.
   */
  V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

  /**
   * Returns the cached value associated with the provided {@code key} if present, or {@code null}
   * otherwise.
   */
  @Nullable
  V get(K key);

  /** Puts the {@code value} into the cache for the {@code key}. */
  void put(K key, V value);

  /** Removes a value for {@code key} if present. */
  void remove(K key);
}

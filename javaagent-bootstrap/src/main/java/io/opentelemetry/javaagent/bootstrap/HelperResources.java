/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static io.opentelemetry.instrumentation.auto.api.WeakMap.Provider.newWeakMap;

import io.opentelemetry.instrumentation.auto.api.WeakMap;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A holder of resources needed by instrumentation. We store them in the bootstrap classloader so
 * instrumentation can store from the agent classloader and apps can retrieve from the app
 * classloader.
 */
public final class HelperResources {

  private static final WeakMap<ClassLoader, Map<String, URL>> RESOURCES = newWeakMap();

  /** Registers the {@code payload} to be available to instrumentation at {@code path}. */
  public static void register(ClassLoader classLoader, String path, URL url) {
    RESOURCES.putIfAbsent(classLoader, new ConcurrentHashMap<String, URL>());
    RESOURCES.get(classLoader).put(path, url);
  }

  /**
   * Returns a {@link URL} that can be used to retrieve the content of the resource at {@code path},
   * or {@code null} if no resource could be found at {@code path}.
   */
  public static URL load(ClassLoader classLoader, String path) {
    Map<String, URL> map = RESOURCES.get(classLoader);
    if (map == null) {
      return null;
    }

    return map.get(path);
  }

  private HelperResources() {}
}

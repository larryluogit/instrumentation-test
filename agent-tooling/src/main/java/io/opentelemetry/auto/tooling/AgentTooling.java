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
package io.opentelemetry.auto.tooling;

import io.opentelemetry.auto.bootstrap.WeakMap;
import io.opentelemetry.auto.tooling.bytebuddy.AgentCachingPoolStrategy;
import io.opentelemetry.auto.tooling.bytebuddy.AgentLocationStrategy;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public class AgentTooling {

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider();
  }

  private static final AgentLocationStrategy LOCATION_STRATEGY = new AgentLocationStrategy();
  private static final AgentCachingPoolStrategy POOL_STRATEGY = new AgentCachingPoolStrategy();

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }

  private static void registerWeakMapProvider() {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent(new Cleaner()));
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
    }
  }
}

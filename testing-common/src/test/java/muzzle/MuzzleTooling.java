/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle;

import io.opentelemetry.javaagent.extension.AgentExtensionTooling;
import io.opentelemetry.javaagent.tooling.AgentTooling;
import io.opentelemetry.javaagent.tooling.Utils;
import net.bytebuddy.pool.TypePool;

public final class MuzzleTooling
    implements AgentExtensionTooling, AgentExtensionTooling.ClassLoaders {

  private static final AgentExtensionTooling INSTANCE = new MuzzleTooling();

  public static AgentExtensionTooling instance() {
    return INSTANCE;
  }

  private MuzzleTooling() {}

  @Override
  public TypePool createTypePool(ClassLoader classLoader) {
    return AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(classLoader), classLoader);
  }

  @Override
  public ClassLoaders classLoaders() {
    return this;
  }

  @Override
  public ClassLoader bootstrapProxyClassLoader() {
    return Utils.getBootstrapProxy();
  }

  @Override
  public ClassLoader agentClassLoader() {
    throw new UnsupportedOperationException("not used by muzzle");
  }
}

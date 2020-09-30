/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.auto.test.utils

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.javaagent.tooling.config.ConfigBuilder
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer
import java.util.concurrent.Callable

class ConfigUtils {

  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    try {
      def existingConfig = Config.get()
      Properties properties = new Properties()
      properties.put(name, value)
      setConfig(new ConfigBuilder()
        .readProperties(existingConfig.asJavaProperties())
        .readProperties(properties)
        .build())
      assert Config.get() != existingConfig
      try {
        return r.call()
      } finally {
        setConfig(existingConfig)
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  /**
   * Provides an callback to set up the testing environment and reset the global configuration after system properties and envs are set.
   */
  static updateConfig(final Callable r) {
    r.call()
    resetConfig()
    AgentTestRunner.resetInstrumentation()
  }

  /**
   * Reset the global configuration. Please note that Runtime ID is preserved to the pre-existing value.
   */
  static void resetConfig() {
    setConfig(Config.DEFAULT)
    ConfigInitializer.initialize()
  }

  private static setConfig(Config config) {
    Config.INSTANCE = config
  }
}

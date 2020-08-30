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

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionLogger {

  private static final Logger log = LoggerFactory.getLogger(VersionLogger.class);

  /** Log version string for java-agent */
  public static void logAllVersions() {
    log.info("opentelemetry-javaagent - version: {}", InstrumentationVersion.VERSION);
    if (log.isDebugEnabled()) {
      log.debug(
          "Running on Java {}. JVM {} - {} - {}",
          System.getProperty("java.version"),
          System.getProperty("java.vm.name"),
          System.getProperty("java.vm.vendor"),
          System.getProperty("java.vm.version"));
    }
  }
}

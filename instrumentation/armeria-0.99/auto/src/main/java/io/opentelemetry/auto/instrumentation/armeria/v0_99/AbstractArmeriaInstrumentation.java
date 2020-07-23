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

package io.opentelemetry.auto.instrumentation.armeria.v0_99;

import io.opentelemetry.auto.tooling.Instrumenter;

public abstract class AbstractArmeriaInstrumentation extends Instrumenter.Default {

  private static final String INSTRUMENTATION_NAME = "armeria";

  public AbstractArmeriaInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".shaded.internal.ContextUtil",
      packageName + ".shaded.server.ArmeriaServerTracer",
      packageName + ".shaded.server.ArmeriaServerTracer$ArmeriaGetter",
      packageName + ".shaded.server.OpenTelemetryService",
      packageName + ".shaded.server.OpenTelemetryService$Decorator",
      // .thenAccept(log -> lambda
      packageName + ".shaded.server.OpenTelemetryService$1",
    };
  }
}

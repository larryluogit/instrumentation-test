/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.reactor.v3_1;

/** Based on Spring Sleuth's Reactor instrumentation. */
public class ContextPropagationOperator
    extends io.opentelemetry.instrumentation.reactor.v3.common.ContextPropagationOperator {

  public ContextPropagationOperator(boolean captureExperimentalSpanAttributes) {
    super(captureExperimentalSpanAttributes);
  }

  public static ContextPropagationOperator create() {
    return builder().build();
  }

  public static ContextPropagationOperatorBuilder builder() {
    return new ContextPropagationOperatorBuilder();
  }
}

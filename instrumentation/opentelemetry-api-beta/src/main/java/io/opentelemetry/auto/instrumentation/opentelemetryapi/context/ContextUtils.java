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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.context;

import io.opentelemetry.instrumentation.auto.api.ContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.context.Scope;

public class ContextUtils {

  private static final Logger log = LoggerFactory.getLogger(ContextUtils.class);

  public static Scope withScopedContext(
      final Context context, final ContextStore<Context, io.grpc.Context> contextStore) {
    io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return NoopScope.getInstance();
    }

    io.opentelemetry.context.Scope scope =
        io.opentelemetry.context.ContextUtils.withScopedContext(shadedContext);
    return new UnshadedScope(scope);
  }
}

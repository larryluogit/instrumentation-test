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

package io.opentelemetry.contrib.http.core;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;
import javax.annotation.Nullable;

/** Used only for testing. */
class TestOnlyMapGetterSetter
    implements HttpTextFormat.Getter<Map<String, String>>,
        HttpTextFormat.Setter<Map<String, String>> {

  @Nullable
  @Override
  public String get(Map<String, String> carrier, String key) {
    return carrier.get(key);
  }

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    carrier.put(key, value);
  }
}

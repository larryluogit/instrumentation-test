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

package io.opentelemetry.instrumentation.auto.httpurlconnection;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.net.HttpURLConnection;

public class HeadersInjectAdapter implements TextMapPropagator.Setter<HttpURLConnection> {

  public static final HeadersInjectAdapter SETTER = new HeadersInjectAdapter();

  @Override
  public void set(HttpURLConnection carrier, String key, String value) {
    carrier.setRequestProperty(key, value);
  }
}

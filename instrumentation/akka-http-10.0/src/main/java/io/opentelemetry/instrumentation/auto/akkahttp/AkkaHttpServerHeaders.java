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

package io.opentelemetry.instrumentation.auto.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Optional;

public class AkkaHttpServerHeaders implements HttpTextFormat.Getter<HttpRequest> {

  public static final AkkaHttpServerHeaders GETTER = new AkkaHttpServerHeaders();

  @Override
  public String get(final HttpRequest carrier, final String key) {
    Optional<HttpHeader> header = carrier.getHeader(key);
    if (header.isPresent()) {
      return header.get().value();
    } else {
      return null;
    }
  }
}

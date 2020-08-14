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

package io.opentelemetry.instrumentation.auto.servlet.v2_2;

import javax.servlet.http.HttpServletResponse;

public class ResponseWithStatus {

  private final HttpServletResponse response;
  private final int status;

  public ResponseWithStatus(HttpServletResponse response, int status) {
    this.response = response;
    this.status = status;
  }

  HttpServletResponse getResponse() {
    return response;
  }

  int getStatus() {
    return status;
  }
}

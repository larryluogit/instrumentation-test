/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.StatusCode;

final class HttpClientStatusConverter implements HttpStatusConverter {

  static final HttpStatusConverter INSTANCE = new HttpClientStatusConverter();

  // https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md#status
  @Override
  public StatusCode statusFromHttpStatus(int httpStatus) {
    if (httpStatus >= 100 && httpStatus < 400) {
      return StatusCode.UNSET;
    }

    return StatusCode.ERROR;
  }

  private HttpClientStatusConverter() {}
}

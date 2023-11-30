/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;

import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.internal.HttpAttributes;
import io.opentelemetry.semconv.SemanticAttributes;

final class HttpExperimentalMetricsAdvice {

  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
  }

  static void applyServerRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                // stable attributes
                SemanticAttributes.HTTP_ROUTE,
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.URL_SCHEME));
  }

  static void applyServerActiveRequestsAdvice(LongUpDownCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongUpDownCounterBuilder)) {
      return;
    }
    ((ExtendedLongUpDownCounterBuilder) builder)
        .setAttributesAdvice(
            asList(
                // https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-metrics.md#metric-httpserveractive_requests
                SemanticAttributes.HTTP_REQUEST_METHOD, SemanticAttributes.URL_SCHEME));
  }

  private HttpExperimentalMetricsAdvice() {}
}

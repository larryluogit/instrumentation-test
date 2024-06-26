/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1.internal;

import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpClientInstrumenterBuilder;
import io.opentelemetry.instrumentation.spring.web.v3_1.SpringWebTelemetryBuilder;
import java.util.function.Function;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class WebTelemetryUtil {
  private WebTelemetryUtil() {}

  public static Function<
          SpringWebTelemetryBuilder,
          DefaultHttpClientInstrumenterBuilder<HttpRequest, ClientHttpResponse>>
      GET_BUILDER;
}
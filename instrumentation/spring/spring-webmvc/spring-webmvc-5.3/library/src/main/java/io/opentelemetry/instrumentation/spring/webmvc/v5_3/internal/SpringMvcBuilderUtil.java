/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.v5_3.internal;

import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetryBuilder;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SpringMvcBuilderUtil {
  private SpringMvcBuilderUtil() {}

  private static Function<
          SpringWebMvcTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
      builderExtractor;

  public static Function<
          SpringWebMvcTelemetryBuilder,
          DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
      getBuilderExtractor() {
    return builderExtractor;
  }

  public static void setBuilderExtractor(
      Function<
              SpringWebMvcTelemetryBuilder,
              DefaultHttpServerInstrumenterBuilder<HttpServletRequest, HttpServletResponse>>
          builderExtractor) {
    SpringMvcBuilderUtil.builderExtractor = builderExtractor;
  }
}

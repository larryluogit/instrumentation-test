/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.StatusCode;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class DefaultStatusExtractorTest {

  @Test
  void noException() {
    assertThat(
            StatusExtractor.getDefault()
                .extract(Collections.emptyMap(), Collections.emptyMap(), null))
        .isEqualTo(StatusCode.UNSET);
  }

  @Test
  void exception() {
    assertThat(
            StatusExtractor.getDefault()
                .extract(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    new IllegalStateException("test")))
        .isEqualTo(StatusCode.ERROR);
  }
}

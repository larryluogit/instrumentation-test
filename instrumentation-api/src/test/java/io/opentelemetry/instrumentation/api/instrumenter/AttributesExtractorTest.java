/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class AttributesExtractorTest {

  static class TestAttributesExtractor
      implements AttributesExtractor<Map<String, String>, Map<String, String>> {

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, Map<String, String> request) {
      attributes.put(AttributeKey.stringKey("animal"), request.get("animal"));
      attributes.put(AttributeKey.stringKey("country"), request.get("country"));
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        Map<String, String> request,
        @Nullable Map<String, String> response,
        @Nullable Throwable error) {
      if (response != null) {
        attributes.put(AttributeKey.stringKey("food"), response.get("food"));
        attributes.put(AttributeKey.stringKey("number"), request.get("number"));
      }
      if (error != null) {
        attributes.put(AttributeKey.stringKey("full_error_class"), error.getClass().getName());
      }
    }
  }

  @Test
  void normal() {
    TestAttributesExtractor extractor = new TestAttributesExtractor();

    Map<String, String> request = new HashMap<>();
    request.put("animal", "cat");
    Map<String, String> response = new HashMap<>();
    response.put("food", "pizza");
    Exception error = new RuntimeException();

    AttributesBuilder attributesBuilder = Attributes.builder();
    Context context = Context.root();

    extractor.onStart(attributesBuilder, context, request);
    extractor.onEnd(attributesBuilder, context, request, response, null);
    extractor.onEnd(attributesBuilder, context, request, null, error);

    assertThat(attributesBuilder.build())
        .containsOnly(
            attributeEntry("animal", "cat"),
            attributeEntry("food", "pizza"),
            attributeEntry("full_error_class", "java.lang.RuntimeException"));
  }
}

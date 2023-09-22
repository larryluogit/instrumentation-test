/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RpcAttributesExtractorTest {

  enum TestGetter implements RpcAttributesGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public String getSystem(Map<String, String> request) {
      return "test";
    }

    @Override
    public String getService(Map<String, String> request) {
      return request.get("service");
    }

    @Override
    public String getMethod(Map<String, String> request) {
      return request.get("method");
    }
  }

  @Test
  void server() {
    testExtractor(RpcServerAttributesExtractor.create(TestGetter.INSTANCE));
  }

  @Test
  void client() {
    testExtractor(RpcClientAttributesExtractor.create(TestGetter.INSTANCE));
  }

  private static void testExtractor(AttributesExtractor<Map<String, String>, Void> extractor) {
    Map<String, String> request = new HashMap<>();
    request.put("service", "my.Service");
    request.put("method", "Method");

    Context context = Context.root();

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, context, request);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "test"),
            entry(SemanticAttributes.RPC_SERVICE, "my.Service"),
            entry(SemanticAttributes.RPC_METHOD, "Method"));
    extractor.onEnd(attributes, context, request, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.RPC_SYSTEM, "test"),
            entry(SemanticAttributes.RPC_SERVICE, "my.Service"),
            entry(SemanticAttributes.RPC_METHOD, "Method"));
  }
}

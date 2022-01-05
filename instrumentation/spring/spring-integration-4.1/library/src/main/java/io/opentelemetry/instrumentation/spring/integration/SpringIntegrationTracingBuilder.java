/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link SpringIntegrationTracing}. */
public final class SpringIntegrationTracingBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-integration-4.1";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<MessageWithChannel, Void>> additionalAttributeExtractors =
      new ArrayList<>();

  SpringIntegrationTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public SpringIntegrationTracingBuilder addAttributesExtractor(
      AttributesExtractor<MessageWithChannel, Void> attributesExtractor) {
    additionalAttributeExtractors.add(attributesExtractor);
    return this;
  }

  private static String consumerSpanName(MessageWithChannel messageWithChannel) {
    return messageWithChannel.getChannelName() + " process";
  }

  private static String producerSpanName(MessageWithChannel messageWithChannel) {
    return messageWithChannel.getChannelName() + " send";
  }

  /**
   * Returns a new {@link SpringIntegrationTracing} with the settings of this {@link
   * SpringIntegrationTracingBuilder}.
   */
  public SpringIntegrationTracing build() {
    Instrumenter<MessageWithChannel, Void> consumerInstrumenter =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                SpringIntegrationTracingBuilder::consumerSpanName)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(SpringMessagingAttributesExtractor.process())
            .newConsumerInstrumenter(MessageHeadersGetter.INSTANCE);

    Instrumenter<MessageWithChannel, Void> producerInstrumenter =
        Instrumenter.<MessageWithChannel, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                SpringIntegrationTracingBuilder::producerSpanName)
            .addAttributesExtractors(additionalAttributeExtractors)
            .addAttributesExtractor(SpringMessagingAttributesExtractor.send())
            .newInstrumenter(SpanKindExtractor.alwaysProducer());
    return new SpringIntegrationTracing(
        openTelemetry.getPropagators(), consumerInstrumenter, producerInstrumenter);
  }
}

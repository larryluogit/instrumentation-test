/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.instrumentation.pulsar.VirtualFieldStore;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.time.Instant;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;

public final class PulsarTelemetry {
  private static final String INSTRUMENTATION = "io.opentelemetry:pulsar-client";
  private static final OpenTelemetry TELEMETRY = GlobalOpenTelemetry.get();
  private static final TextMapPropagator PROPAGATOR =
      TELEMETRY.getPropagators().getTextMapPropagator();

  private static final SpanNameExtractor<Message<?>> CONSUMER_RECEIVE =
      new InternalSpanNameExtractor<>(SpanKind.CONSUMER, MessageOperation.RECEIVE);
  private static final SpanNameExtractor<Message<?>> CONSUMER_PROCESS =
      new InternalSpanNameExtractor<>(SpanKind.CONSUMER, MessageOperation.PROCESS);
  private static final SpanNameExtractor<Message<?>> PRODUCER_SEND =
      new InternalSpanNameExtractor<>(SpanKind.PRODUCER, MessageOperation.SEND);

  private static final Instrumenter<Message<?>, Void> CONSUMER_LISTENER_INSTRUMENTER =
      createConsumerListenerInstrumenter();
  private static final Instrumenter<Message<?>, Attributes> CONSUMER_RECEIVE_INSTRUMENTER =
      createConsumerReceiveInstrumenter();
  private static final Instrumenter<Message<?>, Attributes> PRODUER_INSTRUMENTER =
      createProducerInstrumenter();

  public static Instrumenter<Message<?>, Void> consumerListenerInstrumenter() {
    return CONSUMER_LISTENER_INSTRUMENTER;
  }

  public static Instrumenter<Message<?>, Attributes> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<Message<?>, Attributes> producerInstrumenter() {
    return PRODUER_INSTRUMENTER;
  }

  private static Instrumenter<Message<?>, Attributes> createConsumerReceiveInstrumenter() {
    MessagingAttributesGetter<Message<?>, Attributes> getter =
        ConsumerReceiveAttributeGetter.INSTANCE;
    AttributesExtractor<Message<?>, Attributes> extractor =
        ConsumerReceiveAttributeExtractor.INSTANCE;

    return Instrumenter.<Message<?>, Attributes>builder(
            TELEMETRY, INSTRUMENTATION, CONSUMER_RECEIVE)
        .addAttributesExtractor(extractor)
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(getter, MessageOperation.RECEIVE))
        .buildConsumerInstrumenter(MessageTextMapGetter.INSTANCE);
  }

  private static Instrumenter<Message<?>, Void> createConsumerListenerInstrumenter() {
    MessagingAttributesGetter<Message<?>, Void> getter = ConsumerListenerAttributeGetter.INSTANCE;

    return Instrumenter.<Message<?>, Void>builder(TELEMETRY, INSTRUMENTATION, CONSUMER_PROCESS)
        .addAttributesExtractor(
            MessagingAttributesExtractor.create(getter, MessageOperation.PROCESS))
        .buildInstrumenter();
  }

  private static Instrumenter<Message<?>, Attributes> createProducerInstrumenter() {
    MessagingAttributesGetter<Message<?>, Attributes> getter = ProducerAttributeGetter.INSTANCE;
    AttributesExtractor<Message<?>, Attributes> extractor = ProducerAttributeExtractor.INSTANCE;

    return Instrumenter.<Message<?>, Attributes>builder(TELEMETRY, INSTRUMENTATION, PRODUCER_SEND)
        .addAttributesExtractor(extractor)
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, MessageOperation.SEND))
        .buildProducerInstrumenter(MessageTextMapSetter.INSTANCE);
  }

  public static Context startAndEndConsumerReceive(
      Context parent, Message<?> message, long start, Consumer<?> consumer) {
    if (message == null || !CONSUMER_RECEIVE_INSTRUMENTER.shouldStart(parent, message)) {
      return null;
    }

    String brokerUrl = VirtualFieldStore.extract(consumer);
    Attributes attributes = Attributes.of(SemanticAttributes.MESSAGING_URL, brokerUrl);
    // startAndEnd not supports extract trace context from carrier
    // start not supports custom startTime
    // extract trace context by using TEXT_MAP_PROPAGATOR here.
    return InstrumenterUtil.startAndEnd(
        CONSUMER_RECEIVE_INSTRUMENTER,
        PROPAGATOR.extract(parent, message, MessageTextMapGetter.INSTANCE),
        message,
        attributes,
        null,
        Instant.ofEpochMilli(start),
        Instant.now());
  }

  private PulsarTelemetry() {}

  static class InternalSpanNameExtractor<T> implements SpanNameExtractor<T> {
    private final String name;

    private InternalSpanNameExtractor(SpanKind kind, MessageOperation operation) {
      this.name = kind.name() + "/" + operation.name();
    }

    @Override
    public String extract(T unused) {
      return this.name;
    }
  }
}

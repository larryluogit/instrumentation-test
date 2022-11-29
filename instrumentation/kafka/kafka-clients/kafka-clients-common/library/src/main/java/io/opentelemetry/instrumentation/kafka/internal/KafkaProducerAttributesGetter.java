/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum KafkaProducerAttributesGetter
    implements MessagingAttributesGetter<ProducerRecord<?, ?>, Void> {
  INSTANCE;

  @Override
  public String system(ProducerRecord<?, ?> producerRecord) {
    return "kafka";
  }

  @Override
  public String destinationKind(ProducerRecord<?, ?> producerRecord) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Override
  public String destination(ProducerRecord<?, ?> producerRecord) {
    return producerRecord.topic();
  }

  @Override
  public boolean temporaryDestination(ProducerRecord<?, ?> producerRecord) {
    return false;
  }

  @Override
  @Nullable
  public String protocol(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String protocolVersion(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String url(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String conversationId(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public Long messagePayloadCompressedSize(ProducerRecord<?, ?> producerRecord) {
    return null;
  }

  @Override
  @Nullable
  public String messageId(ProducerRecord<?, ?> producerRecord, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> header(ProducerRecord<?, ?> producerRecord, String name) {
    return StreamSupport.stream(producerRecord.headers().headers(name).spliterator(), false)
        .map(header -> new String(header.value(), StandardCharsets.UTF_8))
        .collect(Collectors.toList());
  }
}

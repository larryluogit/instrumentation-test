/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SingleRecordVertxKafkaTest extends AbstractVertxKafkaTest {

  static final CountDownLatch consumerReady = new CountDownLatch(1);

  @BeforeAll
  static void setUpTopicAndConsumer() {
    kafkaConsumer.handler(
        record -> {
          testing.runWithSpan("consumer", () -> {});
          if (record.value().equals("error")) {
            throw new IllegalArgumentException("boom");
          }
        });

    kafkaConsumer.partitionsAssignedHandler(partitions -> consumerReady.countDown());
    kafkaConsumer.subscribe("testSingleTopic");
  }

  @Test
  void shouldCreateSpansForSingleRecordProcess() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    CountDownLatch sent = new CountDownLatch(1);
    testing.runWithSpan(
        "producer",
        () ->
            sendRecord(
                KafkaProducerRecord.create("testSingleTopic", "10", "testSpan"),
                result -> sent.countDown()));
    assertTrue(sent.await(30, TimeUnit.SECONDS));

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("testSingleTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testSingleTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive")),
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                                AbstractLongAssert::isNotNegative),
                            satisfies(longKey("kafka.offset"), AbstractLongAssert::isNotNegative),
                            satisfies(
                                longKey("kafka.record.queue_time_ms"),
                                AbstractLongAssert::isNotNegative)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }

  @Test
  void shouldHandleFailureInSingleRecordHandler() throws InterruptedException {
    assertTrue(consumerReady.await(30, TimeUnit.SECONDS));

    CountDownLatch sent = new CountDownLatch(1);
    testing.runWithSpan(
        "producer",
        () ->
            sendRecord(
                KafkaProducerRecord.create("testSingleTopic", "10", "error"),
                result -> sent.countDown()));
    assertTrue(sent.await(30, TimeUnit.SECONDS));

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CONSUMER),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer"),
              span ->
                  span.hasName("testSingleTopic send")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic")));

          producer.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("testSingleTopic receive")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive")),
                span ->
                    span.hasName("testSingleTopic process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producer.get().getSpanContext()))
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalArgumentException("boom"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "kafka"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION, "testSingleTopic"),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            satisfies(
                                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                                AbstractLongAssert::isNotNegative),
                            satisfies(
                                SemanticAttributes.MESSAGING_KAFKA_PARTITION,
                                AbstractLongAssert::isNotNegative),
                            satisfies(longKey("kafka.offset"), AbstractLongAssert::isNotNegative),
                            satisfies(
                                longKey("kafka.record.queue_time_ms"),
                                AbstractLongAssert::isNotNegative)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(1))));
  }
}

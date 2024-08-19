/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringIntegrationAndRabbitTest extends AbstractRabbitProducerConsumerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  SpringIntegrationAndRabbitTest() {
    super(null);
  }

  @Test
  void should_cooperate_with_existing_RabbitMq_instrumentation() {
    runWithSpan("parent", () -> producerContext.getBean("producer", Runnable.class).run());

    testing.waitAndAssertTraces(
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace -> {}, // ignore
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("producer").hasParent(trace.getSpan(0)),
                span -> span.hasName("exchange.declare"),
                span ->
                    span.hasName("exchange.declare")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq"))),
                span -> span.hasName("queue.declare"),
                span -> span.hasName("queue.bind"),
                span ->
                    span.hasName("testTopic publish")
                        .hasParent(trace.getSpan(1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("publish")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> s.isInstanceOf(String.class))),
                // spring-cloud-stream-binder-rabbit listener puts all messages into a BlockingQueue
                // immediately after receiving
                // that's why the rabbitmq CONSUMER span will never have any child span (and
                // propagate context, actually)
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("testTopic.anonymous.[-\\w]+ process"))
                        .hasParent(trace.getSpan(6))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            equalTo(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq"),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("process")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                MessagingIncubatingAttributes
                                    .MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY,
                                s -> s.isInstanceOf(String.class))),
                // spring-integration will detect that spring-rabbit has already created a consumer
                // span and back off
                span ->
                    span.hasName("testTopic process")
                        .hasParent(trace.getSpan(6))
                        .hasKind(SpanKind.CONSUMER)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME,
                                s -> s.isEqualTo("testTopic")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_OPERATION,
                                s -> s.isEqualTo("process")),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID,
                                s -> s.isInstanceOf(String.class)),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE,
                                l -> l.isInstanceOf(Long.class))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(8))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("basic.ack")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_ADDRESS,
                                s -> s.isIn("127.0.0.1", "0:0:0:0:0:0:0:1", null)),
                            satisfies(
                                NetworkAttributes.NETWORK_PEER_PORT,
                                l -> l.isInstanceOf(Long.class)),
                            satisfies(
                                NetworkAttributes.NETWORK_TYPE, s -> s.isIn("ipv4", "ipv6", null)),
                            satisfies(
                                MessagingIncubatingAttributes.MESSAGING_SYSTEM,
                                s -> s.isEqualTo("rabbitmq")))));
  }
}
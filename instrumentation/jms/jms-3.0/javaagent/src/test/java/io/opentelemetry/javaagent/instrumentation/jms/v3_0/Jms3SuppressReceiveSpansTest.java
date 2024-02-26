/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

class Jms3SuppressReceiveSpansTest extends AbstractJms3Test {

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void testMessageConsumer(DestinationFactory destinationFactory, boolean isTemporary)
      throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("hello there");

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    // when
    testing.runWithSpan("producer parent", () -> producer.send(sentMessage));

    TextMessage receivedMessage =
        testing.runWithSpan("consumer parent", () -> (TextMessage) consumer.receive());

    // then
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String actualDestinationName = ((ActiveMQDestination) destination).getName();
    // artemis consumers don't know whether the destination is temporary or not
    String producerDestinationName = isTemporary ? "(temporary)" : actualDestinationName;
    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("producer parent").hasNoParent(),
                span ->
                    span.hasName(producerDestinationName + " publish")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                producerDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(actualDestinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasTotalRecordedLinks(0)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                actualDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId))),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("consumer parent").hasNoParent()));
  }
}

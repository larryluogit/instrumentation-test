/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

class Jms3InstrumentationTest {

  static final Logger logger = LoggerFactory.getLogger(Jms3InstrumentationTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static GenericContainer<?> broker;
  static ActiveMQConnectionFactory connectionFactory;
  static Connection connection;
  static Session session;

  @BeforeAll
  static void setUp() throws JMSException {
    broker =
        new GenericContainer<>("quay.io/artemiscloud/activemq-artemis-broker:artemis.2.27.0")
            .withEnv("AMQ_USER", "test")
            .withEnv("AMQ_PASSWORD", "test")
            .withExposedPorts(61616, 8161)
            .withLogConsumer(new Slf4jLogConsumer(logger));
    broker.start();

    connectionFactory =
        new ActiveMQConnectionFactory("tcp://localhost:" + broker.getMappedPort(61616));
    connectionFactory.setUser("test");
    connectionFactory.setPassword("test");

    connection = connectionFactory.createConnection();
    connection.start();

    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
  }

  @AfterAll
  static void tearDown() throws JMSException {
    if (session != null) {
      session.close();
    }
    if (connection != null) {
      connection.close();
    }
    if (connectionFactory != null) {
      connectionFactory.close();
    }
    if (broker != null) {
      broker.close();
    }
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void testMessageConsumer(
      DestinationFactory destinationFactory, String destinationKind, boolean isTemporary)
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

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("producer parent").hasNoParent(),
              span ->
                  span.hasName(producerDestinationName + " send")
                      .hasKind(PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfying(
                          equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                          equalTo(
                              SemanticAttributes.MESSAGING_DESTINATION_NAME,
                              producerDestinationName),
                          equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                          equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                          messagingTempDestination(isTemporary)));

          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("consumer parent").hasNoParent(),
                span ->
                    span.hasName(actualDestinationName + " receive")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasLinks(LinkData.create(producerSpan.get().getSpanContext()))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                actualDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId))));
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void testMessageListener(
      DestinationFactory destinationFactory, String destinationKind, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("hello there");

    MessageProducer producer = session.createProducer(null);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(
        message ->
            testing.runWithSpan(
                "consumer", () -> receivedMessageFuture.complete((TextMessage) message)));

    // when
    testing.runWithSpan("parent", () -> producer.send(destination, sentMessage));

    // then
    TextMessage receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String actualDestinationName = ((ActiveMQDestination) destination).getName();
    // artemis consumers don't know whether the destination is temporary or not
    String producerDestinationName = isTemporary ? "(temporary)" : actualDestinationName;
    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(producerDestinationName + " send")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                producerDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary)),
                span ->
                    span.hasName(actualDestinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                actualDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId)),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  @ArgumentsSource(EmptyReceiveArgumentsProvider.class)
  @ParameterizedTest
  void shouldNotEmitTelemetryOnEmptyReceive(
      DestinationFactory destinationFactory, MessageReceiver receiver) throws JMSException {

    // given
    Destination destination = destinationFactory.create(session);

    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    // when
    Message message = receiver.receive(consumer);

    // then
    assertThat(message).isNull();

    testing.waitForTraces(0);
  }

  @ArgumentsSource(DestinationsProvider.class)
  @ParameterizedTest
  void shouldCaptureMessageHeaders(
      DestinationFactory destinationFactory, String destinationKind, boolean isTemporary)
      throws Exception {

    // given
    Destination destination = destinationFactory.create(session);
    TextMessage sentMessage = session.createTextMessage("hello there");
    sentMessage.setStringProperty("test_message_header", "test");
    sentMessage.setIntProperty("test_message_int_header", 1234);

    MessageProducer producer = session.createProducer(destination);
    cleanup.deferCleanup(producer);
    MessageConsumer consumer = session.createConsumer(destination);
    cleanup.deferCleanup(consumer);

    CompletableFuture<TextMessage> receivedMessageFuture = new CompletableFuture<>();
    consumer.setMessageListener(
        message ->
            testing.runWithSpan(
                "consumer", () -> receivedMessageFuture.complete((TextMessage) message)));

    // when
    testing.runWithSpan("parent", () -> producer.send(sentMessage));

    // then
    TextMessage receivedMessage = receivedMessageFuture.get(10, TimeUnit.SECONDS);
    assertThat(receivedMessage.getText()).isEqualTo(sentMessage.getText());

    String actualDestinationName = ((ActiveMQDestination) destination).getName();
    // artemis consumers don't know whether the destination is temporary or not
    String producerDestinationName = isTemporary ? "(temporary)" : actualDestinationName;
    String messageId = receivedMessage.getJMSMessageID();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span ->
                    span.hasName(producerDestinationName + " send")
                        .hasKind(PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                producerDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            messagingTempDestination(isTemporary),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span ->
                    span.hasName(actualDestinationName + " process")
                        .hasKind(CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfying(
                            equalTo(SemanticAttributes.MESSAGING_SYSTEM, "jms"),
                            equalTo(
                                SemanticAttributes.MESSAGING_DESTINATION_NAME,
                                actualDestinationName),
                            equalTo(SemanticAttributes.MESSAGING_DESTINATION_KIND, destinationKind),
                            equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"),
                            equalTo(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_header"),
                                singletonList("test")),
                            equalTo(
                                stringArrayKey("messaging.header.test_message_int_header"),
                                singletonList("1234"))),
                span -> span.hasName("consumer").hasParent(trace.getSpan(2))));
  }

  private static AttributeAssertion messagingTempDestination(boolean isTemporary) {
    return isTemporary
        ? equalTo(SemanticAttributes.MESSAGING_TEMP_DESTINATION, true)
        : satisfies(SemanticAttributes.MESSAGING_TEMP_DESTINATION, AbstractAssert::isNull);
  }

  static final class EmptyReceiveArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      DestinationFactory topic = session -> session.createTopic("someTopic");
      DestinationFactory queue = session -> session.createQueue("someQueue");
      MessageReceiver receive = consumer -> consumer.receive(100);
      MessageReceiver receiveNoWait = MessageConsumer::receiveNoWait;

      return Stream.of(
          arguments(topic, receive),
          arguments(queue, receive),
          arguments(topic, receiveNoWait),
          arguments(queue, receiveNoWait));
    }
  }

  static final class DestinationsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      DestinationFactory topic = session -> session.createTopic("someTopic");
      DestinationFactory queue = session -> session.createQueue("someQueue");
      DestinationFactory tempTopic = Session::createTemporaryTopic;
      DestinationFactory tempQueue = Session::createTemporaryQueue;

      return Stream.of(
          arguments(topic, "topic", false),
          arguments(queue, "queue", false),
          arguments(tempTopic, "topic", true),
          arguments(tempQueue, "queue", true));
    }
  }

  @FunctionalInterface
  interface DestinationFactory {

    Destination create(Session session) throws JMSException;
  }

  @FunctionalInterface
  interface MessageReceiver {

    Message receive(MessageConsumer consumer) throws JMSException;
  }
}

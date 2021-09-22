/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.ValueMapper
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared

import java.time.Duration
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

class KafkaStreamsSuppressReceiveSpansTest extends AgentInstrumentationSpecification {

  static final STREAM_PENDING = "test.pending"
  static final STREAM_PROCESSED = "test.processed"

  @Shared
  static KafkaContainer kafka
  @Shared
  static Producer<Integer, String> producer
  @Shared
  static Consumer<Integer, String> consumer


  def setupSpec() {
    kafka = new KafkaContainer()
    kafka.start()

    // create test topic
    AdminClient.create(["bootstrap.servers": kafka.bootstrapServers]).withCloseable { admin ->
      admin.createTopics([
        new NewTopic(STREAM_PENDING, 1, (short) 1),
        new NewTopic(STREAM_PROCESSED, 1, (short) 1),
      ]).all().get(10, TimeUnit.SECONDS)
    }

    producer = new KafkaProducer<>(producerProps(kafka.bootstrapServers))

    // values copied from spring's KafkaTestUtils
    def consumerProps = [
      "bootstrap.servers"      : kafka.bootstrapServers,
      "group.id"               : "test",
      "enable.auto.commit"     : "false",
      "auto.commit.interval.ms": "10",
      "session.timeout.ms"     : "60000",
      "key.deserializer"       : IntegerDeserializer,
      "value.deserializer"     : StringDeserializer
    ]
    consumer = new KafkaConsumer<>(consumerProps)

    // assign topic partitions
    consumer.assign([
      new TopicPartition(STREAM_PROCESSED, 0)
    ])
  }

  def cleanupSpec() {
    consumer?.close()
    producer?.close()
    kafka.stop()
  }

  def "test kafka produce and consume with streams in-between"() {
    setup:
    def config = new Properties()
    config.putAll(producerProps(kafka.bootstrapServers))
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.Integer().getClass().getName())
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())

    // CONFIGURE PROCESSOR
    def builder
    try {
      // Different class names for test and latestDepTest.
      builder = Class.forName("org.apache.kafka.streams.kstream.KStreamBuilder").newInstance()
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      builder = Class.forName("org.apache.kafka.streams.StreamsBuilder").newInstance()
    }
    KStream<String, String> textLines = builder.stream(STREAM_PENDING)
    def values = textLines
      .mapValues(new ValueMapper<String, String>() {
        @Override
        String apply(String textLine) {
          Span.current().setAttribute("asdf", "testing")
          return textLine.toLowerCase()
        }
      })

    KafkaStreams streams
    try {
      // Different api for test and latestDepTest.
      values.to(Serdes.String(), Serdes.String(), STREAM_PROCESSED)
      streams = new KafkaStreams(builder, config)
    } catch (MissingMethodException e) {
      def producer = Class.forName("org.apache.kafka.streams.kstream.Produced")
        .with(Serdes.String(), Serdes.String())
      values.to(STREAM_PROCESSED, producer)
      streams = new KafkaStreams(builder.build(), config)
    }
    streams.start()

    when:
    String greeting = "TESTING TESTING 123!"
    producer.send(new ProducerRecord<>(STREAM_PENDING, greeting))

    then:
    // check that the message was received
    def records = consumer.poll(Duration.ofSeconds(10).toMillis())
    Headers receivedHeaders = null
    for (record in records) {
      Span.current().setAttribute("testing", 123)

      assert record.value() == greeting.toLowerCase()
      assert record.key() == null

      if (receivedHeaders == null) {
        receivedHeaders = record.headers()
      }
    }

    SpanData streamSendSpan

    assertTraces(1) {
      trace(0, 4) {
        // kafka-clients PRODUCER
        span(0) {
          name STREAM_PENDING + " send"
          kind PRODUCER
          hasNoParent()
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        // kafka-stream CONSUMER
        span(1) {
          name STREAM_PENDING + " process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PENDING
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
            "asdf" "testing"
          }
        }

        streamSendSpan = span(2)

        // kafka-clients PRODUCER
        span(2) {
          name STREAM_PROCESSED + " send"
          kind PRODUCER
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
          }
        }
        // kafka-clients CONSUMER process
        span(3) {
          name STREAM_PROCESSED + " process"
          kind CONSUMER
          childOf span(2)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "kafka"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" STREAM_PROCESSED
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_KAFKA_PARTITION.key}" { it >= 0 }
            "kafka.offset" 0
            "kafka.record.queue_time_ms" { it >= 0 }
            "testing" 123
          }
        }
      }
    }

    receivedHeaders.iterator().hasNext()
    def traceparent = new String(receivedHeaders.headers("traceparent").iterator().next().value())
    Context context = W3CTraceContextPropagator.instance.extract(Context.root(), "", new TextMapGetter<String>() {
      @Override
      Iterable<String> keys(String carrier) {
        return Collections.singleton("traceparent")
      }

      @Override
      String get(String carrier, String key) {
        if (key == "traceparent") {
          return traceparent
        }
        return null
      }
    })
    def spanContext = Span.fromContext(context).getSpanContext()
    spanContext.traceId == streamSendSpan.traceId
    spanContext.spanId == streamSendSpan.spanId
  }

  private static Map<String, Object> producerProps(String servers) {
    // values copied from spring's KafkaTestUtils
    return [
      "bootstrap.servers": servers,
      "retries"          : 0,
      "batch.size"       : "16384",
      "linger.ms"        : 1,
      "buffer.memory"    : "33554432",
      "key.serializer"   : IntegerSerializer,
      "value.serializer" : StringSerializer
    ]
  }
}

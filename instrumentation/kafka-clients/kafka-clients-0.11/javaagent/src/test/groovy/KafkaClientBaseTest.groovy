/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.IntegerDeserializer
import org.apache.kafka.common.serialization.IntegerSerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class KafkaClientBaseTest extends AgentInstrumentationSpecification {

  protected static final SHARED_TOPIC = "shared.topic"

  private static final boolean propagationEnabled = Boolean.parseBoolean(
    System.getProperty("otel.instrumentation.kafka.client-propagation.enabled", "true"))

  @Shared
  static KafkaContainer kafka
  @Shared
  static Producer<Integer, String> producer
  @Shared
  static Consumer<Integer, String> consumer
  @Shared
  static CountDownLatch consumerReady = new CountDownLatch(1)

  def setupSpec() {
    kafka = new KafkaContainer()
    kafka.start()

    // create test topic
    AdminClient.create(["bootstrap.servers": kafka.bootstrapServers]).withCloseable { admin ->
      admin.createTopics([new NewTopic(SHARED_TOPIC, 1, (short) 1)]).all().get(10, TimeUnit.SECONDS)
    }

    // values copied from spring's KafkaTestUtils
    def producerProps = [
      "bootstrap.servers": kafka.bootstrapServers,
      "retries"          : 0,
      "batch.size"       : "16384",
      "linger.ms"        : 1,
      "buffer.memory"    : "33554432",
      "key.serializer"   : IntegerSerializer,
      "value.serializer" : StringSerializer
    ]
    producer = new KafkaProducer<>(producerProps)

    def consumerProps = [
      "bootstrap.servers"      : kafka.bootstrapServers,
      "group.id"               : "test",
      "enable.auto.commit"     : "true",
      "auto.commit.interval.ms": "10",
      "session.timeout.ms"     : "30000",
      "key.deserializer"       : IntegerDeserializer,
      "value.deserializer"     : StringDeserializer
    ]
    consumer = new KafkaConsumer<>(consumerProps)

    consumer.subscribe([SHARED_TOPIC], new ConsumerRebalanceListener() {
      @Override
      void onPartitionsRevoked(Collection<TopicPartition> collection) {
      }

      @Override
      void onPartitionsAssigned(Collection<TopicPartition> collection) {
        consumerReady.countDown()
      }
    })
  }

  def cleanupSpec() {
    consumer?.close()
    producer?.close()
    kafka.stop()
  }

  @Unroll
  def "test kafka client header propagation manual config"() {
    when:
    String message = "Testing without headers"
    producer.send(new ProducerRecord<>(SHARED_TOPIC, message))
      .get(5, TimeUnit.SECONDS)

    then:
    awaitUntilConsumerIsReady()

    def records = consumer.poll(Duration.ofSeconds(1).toMillis())
    records.count() == 1
    for (record in records) {
      assert record.headers().iterator().hasNext() == propagationEnabled
    }
  }

  // Kafka's eventual consistency behavior forces us to do a couple of empty poll() calls until it gets properly assigned a topic partition
  static void awaitUntilConsumerIsReady() {
    if (consumerReady.await(0, TimeUnit.SECONDS)) {
      return
    }
    for (i in 0..<10) {
      consumer.poll(0)
      if (consumerReady.await(1, TimeUnit.SECONDS)) {
        break
      }
    }
    if (consumerReady.getCount() != 0) {
      throw new AssertionError("Consumer wasn't assigned any partitions!")
    }
    consumer.seekToBeginning([])
  }
}

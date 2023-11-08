/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.SemanticAttributes
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

abstract class AbstractSqsTracingTest extends InstrumentationSpecification {

  abstract AmazonSQSAsyncClientBuilder configureClient(AmazonSQSAsyncClientBuilder client)

  @Shared
  def sqs
  @Shared
  AmazonSQSAsyncClient client
  @Shared
  int sqsPort

  def setupSpec() {

    sqsPort = PortUtils.findOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

    def credentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"))
    def endpointConfiguration = new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq")
    client = configureClient(AmazonSQSAsyncClient.asyncBuilder()).withCredentials(credentials).withEndpointConfiguration(endpointConfiguration).build()
  }

  def cleanupSpec() {
    if (sqs != null) {
      sqs.stopAndWait()
    }
  }

  def "simple sqs producer-consumer services #testCaptureHeaders"() {
    setup:
    client.createQueue("testSdkSqs")

    when:
    SendMessageRequest sendMessageRequest = new SendMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs", "{\"type\": \"hello\"}")
    if (testCaptureHeaders) {
      sendMessageRequest.addMessageAttributesEntry("test-message-header", new MessageAttributeValue().withDataType("String").withStringValue("test"))
    }
    client.sendMessage(sendMessageRequest)
    ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs")
    if (testCaptureHeaders) {
      receiveMessageRequest.withMessageAttributeNames("test-message-header")
    }
    def receiveMessageResult = client.receiveMessage(receiveMessageRequest)
    receiveMessageResult.messages.each {message ->
      runWithSpan("process child") {}
    }

    then:
    assertTraces(3) {
      SpanData publishSpan
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.queue.name" "testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "CreateQueue"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "testSdkSqs publish"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.method" "SendMessage"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "publish"
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            if (testCaptureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        publishSpan = span(0)
      }
      trace(2, 3) {
        span(0) {
          name "testSdkSqs receive"
          kind CONSUMER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            if (testCaptureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(1) {
          name "testSdkSqs process"
          kind CONSUMER
          childOf span(0)
          hasLink(publishSpan)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
            if (testCaptureHeaders) {
              "messaging.header.test_message_header" { it == ["test"] }
            }
          }
        }
        span(2) {
          name "process child"
          childOf span(1)
          attributes {
          }
        }
      }
    }

    where:
    testCaptureHeaders << [false, true]
  }

  def "simple sqs producer-consumer services with parent span"() {
    setup:
    client.createQueue("testSdkSqs")

    when:
    SendMessageRequest send = new SendMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs", "{\"type\": \"hello\"}")
    client.sendMessage(send)
    runWithSpan("parent") {
      def receiveMessageResult = client.receiveMessage("http://localhost:$sqsPort/000000000000/testSdkSqs")
      receiveMessageResult.messages.each {message ->  runWithSpan("process child") {}}
    }

    then:
    assertTraces(3) {
      SpanData publishSpan
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.queue.name" "testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "rpc.method" "CreateQueue"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "testSdkSqs publish"
          kind PRODUCER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.method" "SendMessage"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "publish"
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
        publishSpan = span(0)
      }
      trace(2, 5) {
        span(0) {
          name "parent"
          hasNoParent()
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
        span(2) {
          name "testSdkSqs receive"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "receive"
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
          }
        }
        span(3) {
          name "testSdkSqs process"
          kind CONSUMER
          childOf span(2)
          hasLink(publishSpan)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/testSdkSqs"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.method" "POST"
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "$SemanticAttributes.MESSAGING_SYSTEM" "AmazonSQS"
            "$SemanticAttributes.MESSAGING_DESTINATION_NAME" "testSdkSqs"
            "$SemanticAttributes.MESSAGING_OPERATION" "process"
          }
        }
        span(4) {
          name "process child"
          childOf span(3)
          attributes {
          }
        }
      }
    }
  }

  def "only adds attribute name once when request reused"() {
    setup:
    client.createQueue("testSdkSqs2")

    when:
    SendMessageRequest send = new SendMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs2", "{\"type\": \"hello\"}")
    client.sendMessage(send)
    ReceiveMessageRequest receive = new ReceiveMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs2")
    client.receiveMessage(receive)
    client.sendMessage(send)
    client.receiveMessage(receive)

    then:
    receive.getAttributeNames() == ["AWSTraceHeader"]
  }
}

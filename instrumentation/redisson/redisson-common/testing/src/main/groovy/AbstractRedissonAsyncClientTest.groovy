/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.junit.Assume
import org.redisson.Redisson
import org.redisson.api.BatchOptions
import org.redisson.api.RBatch
import org.redisson.api.RBucket
import org.redisson.api.RFuture
import org.redisson.api.RScheduledExecutorService
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.SingleServerConfig
import org.testcontainers.containers.GenericContainer
import spock.lang.Shared

import java.util.concurrent.Callable
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

abstract class AbstractRedissonAsyncClientTest extends AgentInstrumentationSpecification {

  private static GenericContainer redisServer = new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379)
  @Shared
  int port

  @Shared
  RedissonClient redisson
  @Shared
  String address

  def setupSpec() {
    redisServer.start()
    port = redisServer.getMappedPort(6379)
    address = "localhost:" + port
    if (useRedisProtocol()) {
      // Newer versions of redisson require scheme, older versions forbid it
      address = "redis://" + address
    }
  }

  def cleanupSpec() {
    redisson.shutdown()
    redisServer.stop()
  }

  def setup() {
    Config config = new Config()
    SingleServerConfig singleServerConfig = config.useSingleServer()
    singleServerConfig.setAddress(address)
    singleServerConfig.setTimeout(30_000)
    // disable connection ping if it exists
    singleServerConfig.metaClass.getMetaMethod("setPingConnectionInterval", int)?.invoke(singleServerConfig, 0)
    redisson = Redisson.create(config)
    clearExportedData()
  }

  boolean useRedisProtocol() {
    return Boolean.getBoolean("testLatestDeps")
  }

  def "test future set"() {
    when:
    RBucket<String> keyObject = redisson.getBucket("foo")
    RFuture future = keyObject.setAsync("bar")
    future.get(30, TimeUnit.SECONDS)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SET"
          kind CLIENT
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET foo ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
          }
        }
      }
    }
  }

  def "test future whenComplete"() {
    when:
    RSet<String> rSet = redisson.getSet("set1")
    CompletionStage<Boolean> result = runWithSpan("parent") {
      RFuture<Boolean> future = rSet.addAsync("s1")
      return future.whenComplete({ res, throwable ->
        if (!Span.current().getSpanContext().isValid()) {
          new Exception("Callback should have a parent span.").printStackTrace()
        }
        runWithSpan("callback") {
        }
      })
    }

    then:
    result.toCompletableFuture().get(30, TimeUnit.SECONDS)
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "SADD"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SADD set1 ?"
            "$SemanticAttributes.DB_OPERATION" "SADD"
          }
        }
        span(2) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6033
  def "test schedule"() {
    RScheduledExecutorService executorService = redisson.getExecutorService("EXECUTOR")
    def taskId = executorService.schedule(new MyCallable(), 0, TimeUnit.SECONDS)
      .getTaskId()
    expect:
    taskId != null
  }

  private static class MyCallable implements Callable, Serializable {

    @Override
    Object call() throws Exception {
      return null
    }
  }

  def "test atomic batch command"() {
    try {
      // available since 3.7.2
      Class.forName('org.redisson.api.BatchOptions$ExecutionMode')
    } catch (ClassNotFoundException exception) {
      Assume.assumeNoException(exception)
    }

    when:
    CompletionStage<Boolean> result = runWithSpan("parent") {
      def batchOptions = BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC)
      RBatch batch = redisson.createBatch(batchOptions)
      batch.getBucket("batch1").setAsync("v1")
      batch.getBucket("batch2").setAsync("v2")
      RFuture<Boolean> future = batch.executeAsync()
      return future.whenComplete({ res, throwable ->
        if (!Span.current().getSpanContext().isValid()) {
          new Exception("Callback should have a parent span.").printStackTrace()
        }
        runWithSpan("callback") {
        }
      })
    }

    then:
    result.toCompletableFuture().get(30, TimeUnit.SECONDS)
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "DB Query"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "MULTI;SET batch1 ?"
            "$SemanticAttributes.DB_OPERATION" null
          }
        }
        span(2) {
          name "SET"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "SET batch2 ?"
            "$SemanticAttributes.DB_OPERATION" "SET"
          }
        }
        span(3) {
          name "EXEC"
          kind CLIENT
          childOf(span(0))
          attributes {
            "$SemanticAttributes.NET_SOCK_PEER_ADDR" "127.0.0.1"
            "$SemanticAttributes.NET_SOCK_PEER_NAME" "localhost"
            "$SemanticAttributes.NET_SOCK_PEER_PORT" port
            "$SemanticAttributes.DB_SYSTEM" "redis"
            "$SemanticAttributes.DB_STATEMENT" "EXEC"
            "$SemanticAttributes.DB_OPERATION" "EXEC"
          }
        }
        span(4) {
          name "callback"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }
  }
}

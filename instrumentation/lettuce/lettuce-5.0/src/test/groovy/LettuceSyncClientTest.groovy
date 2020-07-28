/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import io.opentelemetry.trace.attributes.StringAttributeSetter
import redis.embedded.RedisServer
import spock.lang.Shared

import java.util.concurrent.CompletionException

import static io.opentelemetry.trace.Span.Kind.CLIENT

class LettuceSyncClientTest extends AgentTestRunner {
  public static final String PEER_NAME = "localhost"
  public static final String PEER_IP = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  int port
  @Shared
  int incorrectPort
  @Shared
  String dbAddr
  @Shared
  String dbAddrNonExistent
  @Shared
  String dbUriNonExistent
  @Shared
  String embeddedDbUri

  @Shared
  RedisServer redisServer

  @Shared
  Map<String, String> testHashMap = [
    firstname: "John",
    lastname : "Doe",
    age      : "53"
  ]

  RedisClient redisClient
  StatefulConnection connection
  RedisCommands<String, ?> syncCommands

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    incorrectPort = PortUtils.randomOpenPort()
    dbAddr = PEER_NAME + ":" + port + "/" + DB_INDEX
    dbAddrNonExistent = PEER_NAME + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent
    embeddedDbUri = "redis://" + dbAddr

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + PEER_NAME)
    // set max memory to avoid problems in CI
      .setting("maxmemory 128M")
      .port(port).build()
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)

    redisServer.start()
    connection = redisClient.connect()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")
    syncCommands.hmset("TESTHM", testHashMap)

    // 2 sets + 1 connect trace
    TEST_WRITER.waitForTraces(3)
    TEST_WRITER.clear()
  }

  def cleanup() {
    connection.close()
    redisServer.stop()
  }

  def "connect"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    StatefulConnection connection = testConnectionClient.connect()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "CONNECT"
          spanKind CLIENT
          errored false
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" PEER_NAME
            "${SemanticAttributes.NET_PEER_IP.key()}" PEER_IP
            "${SemanticAttributes.NET_PEER_PORT.key()}" port
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "CONNECT"
            "db.redis.dbIndex" 0
          }
        }
      }
    }

    cleanup:
    connection.close()
  }

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    testConnectionClient.connect()

    then:
    thrown RedisConnectionException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "CONNECT"
          spanKind CLIENT
          errored true
          errorEvent CompletionException, String
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" PEER_NAME
            "${SemanticAttributes.NET_PEER_IP.key()}" PEER_IP
            "${SemanticAttributes.NET_PEER_PORT.key()}" incorrectPort
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "CONNECT"
            "db.redis.dbIndex" 0
          }
        }
      }
    }
  }

  def "set command"() {
    setup:
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL")

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SET"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SET"
          }
        }
      }
    }
  }

  def "get command"() {
    setup:
    String res = syncCommands.get("TESTKEY")

    expect:
    res == "TESTVAL"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
          }
        }
      }
    }
  }

  def "get non existent key command"() {
    setup:
    String res = syncCommands.get("NON_EXISTENT_KEY")

    expect:
    res == null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "GET"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "GET"
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    setup:
    def keyRetrieved = syncCommands.randomkey()

    expect:
    keyRetrieved != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "RANDOMKEY"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "RANDOMKEY"
          }
        }
      }
    }
  }

  def "list command"() {
    setup:
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT")

    expect:
    res == 1
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "LPUSH"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "LPUSH"
          }
        }
      }
    }
  }

  def "hash set command"() {
    setup:
    def res = syncCommands.hmset("user", testHashMap)

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "HMSET"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "HMSET"
          }
        }
      }
    }
  }

  def "hash getall command"() {
    setup:
    Map<String, String> res = syncCommands.hgetall("TESTHM")

    expect:
    res == testHashMap
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "HGETALL"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "HGETALL"
          }
        }
      }
    }
  }

  def "debug segfault command (returns void) with no argument should produce span"() {
    setup:
    syncCommands.debugSegfault()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "DEBUG"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "DEBUG"
          }
        }
      }
    }
  }

  def "shutdown command (returns void) should produce a span"() {
    setup:
    syncCommands.shutdown(false)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "SHUTDOWN"
          spanKind CLIENT
          errored false
          attributes {
            "${StringAttributeSetter.create("db.system").key()}" "redis"
            "${SemanticAttributes.DB_STATEMENT.key()}" "SHUTDOWN"
          }
        }
      }
    }
  }
}

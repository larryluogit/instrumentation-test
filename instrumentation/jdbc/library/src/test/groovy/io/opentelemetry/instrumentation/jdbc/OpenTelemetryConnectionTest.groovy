/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc

import io.opentelemetry.instrumentation.jdbc.internal.*
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

class OpenTelemetryConnectionTest extends InstrumentationSpecification implements LibraryTestTrait {

  def "verify create statement"() {
    setup:
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo)
    String query = "SELECT * FROM users"
    def statement = connection.createStatement()
    runUnderTrace("parent") {
      assert statement.execute(query)
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" dbInfo.system
            "$SemanticAttributes.DB_NAME.key" dbInfo.name
            "$SemanticAttributes.DB_USER.key" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING.key" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME.key" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT.key" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT.key" query
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify create statement returns otel wrapper"() {
    when:
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT)

    then:
    connection.createStatement().class == OpenTelemetryStatement
    connection.createStatement(0, 0).class == OpenTelemetryStatement
    connection.createStatement(0, 0, 0).class == OpenTelemetryStatement
  }

  def "verify prepare statement"() {
    setup:
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo)
    String query = "SELECT * FROM users"
    def statement = connection.prepareStatement(query)
    runUnderTrace("parent") {
      assert statement.execute()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" dbInfo.system
            "$SemanticAttributes.DB_NAME.key" dbInfo.name
            "$SemanticAttributes.DB_USER.key" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING.key" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME.key" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT.key" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT.key" query
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify prepare statement returns otel wrapper"() {
    when:
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT)

    then:
    connection.prepareStatement("SELECT * FROM users").class == OpenTelemetryPreparedStatement
    connection.prepareStatement("SELECT * FROM users", [0] as int[]).class == OpenTelemetryPreparedStatement
    connection.prepareStatement("SELECT * FROM users", ["id"] as String[]).class == OpenTelemetryPreparedStatement
    connection.prepareStatement("SELECT * FROM users", 0).class == OpenTelemetryPreparedStatement
    connection.prepareStatement("SELECT * FROM users", 0, 0).class == OpenTelemetryPreparedStatement
    connection.prepareStatement("SELECT * FROM users", 0, 0, 0).class == OpenTelemetryPreparedStatement
  }

  def "verify prepare call"() {
    setup:
    def dbInfo = getDbInfo()
    def connection = new OpenTelemetryConnection(new TestConnection(), dbInfo)
    String query = "SELECT * FROM users"
    def statement = connection.prepareCall(query)
    runUnderTrace("parent") {
      assert statement.execute()
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          name "SELECT my_name.users"
          kind CLIENT
          childOf span(0)
          attributes {
            "$SemanticAttributes.DB_SYSTEM.key" dbInfo.system
            "$SemanticAttributes.DB_NAME.key" dbInfo.name
            "$SemanticAttributes.DB_USER.key" dbInfo.user
            "$SemanticAttributes.DB_CONNECTION_STRING.key" dbInfo.shortUrl
            "$SemanticAttributes.NET_PEER_NAME.key" dbInfo.host
            "$SemanticAttributes.NET_PEER_PORT.key" dbInfo.port
            "$SemanticAttributes.DB_STATEMENT.key" query
            "$SemanticAttributes.DB_OPERATION.key" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE.key" "users"
          }
        }
      }
    }

    cleanup:
    statement.close()
    connection.close()
  }

  def "verify prepare call returns otel wrapper"() {
    when:
    def connection = new OpenTelemetryConnection(new TestConnection(), DbInfo.DEFAULT)

    then:
    connection.prepareCall("SELECT * FROM users").class == OpenTelemetryCallableStatement
    connection.prepareCall("SELECT * FROM users", 0, 0).class == OpenTelemetryCallableStatement
    connection.prepareCall("SELECT * FROM users", 0, 0, 0).class == OpenTelemetryCallableStatement
  }

  private DbInfo getDbInfo() {
    DbInfo.builder()
        .system("my_system")
        .subtype("my_sub_type")
        .shortUrl("my_connection_string")
        .user("my_user")
        .name("my_name")
        .db("my_db")
        .host("my_host")
        .port(1234)
        .build()
  }

}

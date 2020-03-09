/*
 * Copyright 2020, OpenTelemetry Authors
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
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.jpa.Customer
import spring.jpa.CustomerRepository
import spring.jpa.PersistenceConfig

import static io.opentelemetry.trace.Span.Kind.CLIENT

/**
 * Unfortunately this test verifies that our hibernate instrumentation doesn't currently work with Spring Data Repositories.
 */
class SpringJpaTest extends AgentTestRunner {

  @Shared
  def context = new AnnotationConfigApplicationContext(PersistenceConfig)

  @Shared
  def repo = context.getBean(CustomerRepository)

  def "test CRUD"() {
    setup:
    def customer = new Customer("Bob", "Anonymous")

    expect:
    customer.id == null
    !repo.findAll().iterator().hasNext()

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.save(customer)
    def savedId = customer.id

    then:
    customer.id != null
    // Behavior changed in new version:
    def extraTrace = TEST_WRITER.traces.size() == 2
    assertTraces(extraTrace ? 2 : 1) {
      if (extraTrace) {
        trace(0, 1) {
          span(0) {
            spanKind CLIENT
            tags {
              "$MoreTags.SERVICE_NAME" "hsqldb"
              "$MoreTags.RESOURCE_NAME" "call next value for hibernate_sequence"
              "$MoreTags.SPAN_TYPE" "sql"
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
              "$Tags.DB_TYPE" "hsqldb"
              "$Tags.DB_INSTANCE" "test"
              "$Tags.DB_USER" "sa"
              "$Tags.DB_STATEMENT" "call next value for hibernate_sequence"
              "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            }
          }
        }
      }
      trace(extraTrace ? 1 : 0, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/insert into Customer \(.*\) values \(.*, \?, \?\)/
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/insert into Customer \(.*\) values \(.*, \?, \?\)/
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer.firstName = "Bill"
    repo.save(customer)

    then:
    customer.id == savedId
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "update Customer set firstName=?, lastName=? where id=?"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "update Customer set firstName=?, lastName=? where id=?"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer = repo.findByLastName("Anonymous")[0]

    then:
    customer.id == savedId
    customer.firstName == "Bill"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_ where customer0_.lastName=?"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_ where customer0_.lastName=?"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete(customer)

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          spanKind CLIENT
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" "delete from Customer where id=?"
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" "delete from Customer where id=?"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()
  }
}

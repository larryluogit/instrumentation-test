/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.apachecamel.RetryOnAddressAlreadyInUse;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CassandraTest extends RetryOnAddressAlreadyInUse {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext server;

  @Container
  private static final CassandraContainer<?> cassandra =
      new CassandraContainer<>("cassandra:3.11.2").withExposedPorts(9042);

  private static String host;

  private static Integer port;

  private static CqlSession cqlSession;

  @BeforeAll
  public static void setupSpec() {
    withRetryOnAddressAlreadyInUse(CassandraTest::setupSpecUnderRetry);
  }

  private static void cassandraSetup() {
    cqlSession =
        CqlSession.builder()
            .addContactPoint(cassandra.getContactPoint())
            .withLocalDatacenter(cassandra.getLocalDatacenter())
            .build();

    cqlSession.execute(
        "CREATE KEYSPACE IF NOT EXISTS test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};");
    cqlSession.execute("CREATE TABLE IF NOT EXISTS test.users (id int PRIMARY KEY, name TEXT);");
  }

  public static void setupSpecUnderRetry() {
    cassandra.start();
    cassandraSetup();

    port = cassandra.getFirstMappedPort();
    host = cassandra.getHost();

    SpringApplication app = new SpringApplication(CassandraConfig.class);
    app.setDefaultProperties(ImmutableMap.of("cassandra.host", host, "cassandra.port", port));
    server = app.run();
  }

  @AfterAll
  public static void cleanupSpec() {
    if (server != null) {
      server.close();
      server = null;
    }
    cqlSession.close();
    cassandra.stop();
  }

  @Test
  public void testCassandra() {
    CamelContext camelContext = server.getBean(CamelContext.class);
    ProducerTemplate template = camelContext.createProducerTemplate();

    template.requestBody("direct:input", (Object) null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttribute(AttributeKey.stringKey("camel.uri"), "direct://input"),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfying(
                            equalTo(
                                AttributeKey.stringKey("camel.uri"),
                                "cql://" + host + ":" + port + "/test"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(
                                SemanticAttributes.DB_STATEMENT,
                                "select * from test.users where id=? ALLOW FILTERING"),
                            equalTo(SemanticAttributes.DB_SYSTEM, "cassandra"))));
  }
}

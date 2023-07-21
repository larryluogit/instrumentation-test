/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

abstract class AbstractHibernateTest extends AgentInstrumentationSpecification {

  static final String VALUE_CLASS = "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value";

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();
  protected static SessionFactory sessionFactory;
  protected static List<Value> prepopulated;

  @BeforeAll
  static void setUp() {
    sessionFactory = new AnnotationConfiguration().configure().buildSessionFactory();

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i));
      writer.save(prepopulated.get(i));
    }
    writer.getTransaction().commit();
    writer.close();
  }

  @AfterAll
  static void cleanUp() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  static SpanDataAssert assertClientSpan(SpanDataAssert span, SpanData parent) {
    return span.hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
            equalTo(SemanticAttributes.DB_NAME, "db1"),
            equalTo(SemanticAttributes.DB_USER, "sa"),
            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
            satisfies(SemanticAttributes.DB_STATEMENT, val -> val.isInstanceOf(String.class)),
            satisfies(SemanticAttributes.DB_OPERATION, val -> val.isInstanceOf(String.class)),
            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value"));
  }

  static SpanDataAssert assertClientSpan(
      SpanDataAssert span, SpanData parent, String verb) {
    return span.hasName(verb.concat(" db1.Value"))
        .hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
            equalTo(SemanticAttributes.DB_NAME, "db1"),
            equalTo(SemanticAttributes.DB_USER, "sa"),
            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
            satisfies(
                SemanticAttributes.DB_STATEMENT,
                stringAssert -> stringAssert.startsWith(verb.toLowerCase(Locale.ROOT))),
            equalTo(SemanticAttributes.DB_OPERATION, verb),
            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value"));
  }

  static SpanDataAssert assertSessionSpan(
      SpanDataAssert span, SpanData parent, String spanName) {
    return span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            satisfies(
                AttributeKey.stringKey("hibernate.session_id"),
                val -> val.isInstanceOf(String.class)));
  }

  static SpanDataAssert assertSpanWithSessionId(
      SpanDataAssert span, SpanData parent, String spanName, String sessionId) {
    return span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(AttributeKey.stringKey("hibernate.session_id"), sessionId));
  }

}

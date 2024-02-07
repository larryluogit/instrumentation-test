/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CriteriaTest extends AbstractHibernateTest {
  private static Stream<Arguments> provideParameters() {
    List<Consumer<Query<Value>>> interactions =
        Arrays.asList(Query::getResultList, Query::uniqueResult, Query::getSingleResultOrNull);

    return Stream.of(
        Arguments.of(named("getResultList", interactions.get(0))),
        Arguments.of(named("uniqueResult", interactions.get(1))),
        Arguments.of(named("getSingleResultOrNull", interactions.get(2))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideParameters")
  void testCriteriaQuery(Consumer<Query<Value>> interaction) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder();
          CriteriaQuery<Value> createQuery = criteriaBuilder.createQuery(Value.class);
          Root<Value> root = createQuery.from(Value.class);
          createQuery
              .select(root)
              .where(criteriaBuilder.like(root.get("name"), "Hello"))
              .orderBy(criteriaBuilder.desc(root.get("name")));
          Query<Value> query = session.createQuery(createQuery);
          interaction.accept(query);
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace
                .hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(
                                "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                satisfies(
                                    AttributeKey.stringKey("hibernate.session_id"),
                                    val -> val.isInstanceOf(String.class))),
                    span ->
                        span.hasName("SELECT db1.Value")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
                                equalTo(SemanticAttributes.DB_NAME, "db1"),
                                equalTo(SemanticAttributes.DB_USER, "sa"),
                                equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
                                satisfies(
                                    SemanticAttributes.DB_STATEMENT,
                                    stringAssert -> stringAssert.startsWith("select")),
                                equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                                equalTo(SemanticAttributes.DB_SQL_TABLE, "Value")),
                    span ->
                        span.hasName("Transaction.commit")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    AttributeKey.stringKey("hibernate.session_id"),
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(AttributeKey.stringKey("hibernate.session_id"))))));
  }
}

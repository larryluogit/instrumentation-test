package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import org.hibernate.Query;
import org.hibernate.Session;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Named.named;

class QueryTest extends AbstractHibernateTest {

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testHibernateQuery(Parameter parameters) {

    // With Transaction
    if (parameters.requiresTransaction) {
      testing.runWithSpan(
          "parent",
          () -> {
            Session session = sessionFactory.openSession();
            session.beginTransaction();
            parameters.queryInteraction.accept(session);
            session.getTransaction().commit();
            session.close();
          });
    }
    // Without Transaction
    else {
      testing.runWithSpan(
          "parent2",
          () -> {
            Session session = sessionFactory.openSession();
            parameters.queryInteraction.accept(session);
            session.close();
          });
    }

    testing.waitAndAssertTraces(
        trace -> transactionAwareAssertions(parameters.requiresTransaction, trace, parameters.expectedSpanName));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(named("Query.list", new Parameter(
            "SELECT " + VALUE_CLASS,
            false,
            sess -> {
              Query q =
                  sess.createQuery(
                      "from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value where id = ?");
              q.setParameter(0, 1L);
              q.uniqueResult();
            })
        )),
        Arguments.of(named("Query.executeUpdate", new Parameter(
            "UPDATE " + VALUE_CLASS,
            true,
            sess -> {
              Query q = sess.createQuery("update io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value set name = ?");
              q.setParameter(0, "alyx");
              q.executeUpdate();
            })
        )),
        Arguments.of(named("Query.uniqueResult", new Parameter(
            "SELECT " + VALUE_CLASS,
            false,
            sess -> {
              Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value where id = ?");
              q.setParameter(0, 1L);
              q.uniqueResult();
            })
        )),
        Arguments.of(named("Query.iterate", new Parameter(
            "SELECT " + VALUE_CLASS,
            false,
            sess -> {
              Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value");
              q.iterate();
            })
        )),
        Arguments.of(named("Query.scroll", new Parameter(
            "SELECT " + VALUE_CLASS,
            false,
            sess -> {
              Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value");
              q.scroll();
            })
        ))
    );
  }

  private static class Parameter {
    public final String expectedSpanName;
    public final boolean requiresTransaction;
    public final Consumer<Session> queryInteraction;

    public Parameter(
        String expectedSpanName,
        boolean requiresTransaction,
        Consumer<Session> queryInteraction) {
      this.expectedSpanName = expectedSpanName;
      this.requiresTransaction = requiresTransaction;
      this.queryInteraction = queryInteraction;
    }
  }

  private static TraceAssert transactionAwareAssertions(boolean requiresTransaction, TraceAssert trace, String expectedSpanName) {
    if (requiresTransaction) {
      return  trace.hasSpansSatisfyingExactly(
          span -> span.hasName("parent")
              .hasKind(SpanKind.INTERNAL)
              .hasNoParent(),
          span -> assertSessionSpan(span, trace.getSpan(0), expectedSpanName),
          span -> assertClientSpan(span, trace.getSpan(1)),
          span ->
              assertSpanWithSessionId(
                  span,
                  trace.getSpan(0),
                  "Transaction.commit",
                  trace
                      .getSpan(1)
                      .getAttributes()
                      .get(AttributeKey.stringKey("hibernate.session_id"))));
    }

    // Without Transaction
    return trace.hasSpansSatisfyingExactly(
        span -> span.hasName("parent2")
            .hasKind(SpanKind.INTERNAL)
            .hasNoParent(),
        span -> assertSessionSpan(span, trace.getSpan(0), expectedSpanName),
        span -> assertClientSpan(span, trace.getSpan(1), "SELECT"));
  }
}

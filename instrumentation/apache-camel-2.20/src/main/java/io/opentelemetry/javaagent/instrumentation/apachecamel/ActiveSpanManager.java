/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import org.apache.camel.Exchange;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utility class for managing active spans as a stack associated with an exchange. */
class ActiveSpanManager {

  private static final String ACTIVE_SPAN_PROPERTY = "OpenTelemetry.activeSpan";

  private static final Logger LOG = LoggerFactory.getLogger(ActiveSpanManager.class);

  private ActiveSpanManager() {}

  public static Span getSpan(Exchange exchange) {
    SpanWithScope spanWithScope = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);
    if (spanWithScope != null) {
      return spanWithScope.getSpan();
    }
    return null;
  }

  /**
   * This method activates the supplied span for the supplied exchange. If an existing span is found
   * for the exchange, this will be pushed onto a stack.
   *
   * @param exchange The exchange
   * @param span The span
   */
  public static void activate(Exchange exchange, Span span) {

    SpanWithScope parent = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);
    SpanWithScope spanWithScope = SpanWithScope.activate(span, parent);
    exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Activated span: " + spanWithScope);
    }
  }

  /**
   * This method deactivates an existing active span associated with the supplied exchange. Once
   * deactivated, if a parent span is found associated with the stack for the exchange, it will be
   * restored as the current span for that exchange.
   *
   * @param exchange The exchange
   */
  public static void deactivate(Exchange exchange) {

    SpanWithScope spanWithScope = exchange.getProperty(ACTIVE_SPAN_PROPERTY, SpanWithScope.class);
    if (spanWithScope != null) {
      spanWithScope.deactivate();
      exchange.setProperty(ACTIVE_SPAN_PROPERTY, spanWithScope.getParent());
      if (LOG.isTraceEnabled()) {
        LOG.trace("Deactivated span: " + spanWithScope);
      }
    }
  }

  public static class SpanWithScope {
    @Nullable private SpanWithScope parent;
    private Span span;
    private Scope scope;

    public SpanWithScope(SpanWithScope parent, Span span, Scope scope) {
      this.parent = parent;
      this.span = span;
      this.scope = scope;
    }

    public static SpanWithScope activate(Span span, SpanWithScope parent) {
      Scope scope = CamelTracer.TRACER.startScope(span);
      return new SpanWithScope(parent, span, scope);
    }

    public SpanWithScope getParent() {
      return parent;
    }

    public Span getSpan() {
      return span;
    }

    public void deactivate() {
      scope.close();
      span.end();
    }

    @Override
    public String toString() {
      return "SpanWithScope [span=" + span + ", scope=" + scope + "]";
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.log4j.v2_7;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SAMPLED;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;

import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.List;
import org.apache.logging.log4j.core.ContextDataInjector;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

public final class SpanDecoratingContextDataInjector implements ContextDataInjector {
  private final ContextDataInjector delegate;

  public SpanDecoratingContextDataInjector(ContextDataInjector delegate) {
    this.delegate = delegate;
  }

  @Override
  public StringMap injectContextData(List<Property> list, StringMap stringMap) {
    StringMap contextData = delegate.injectContextData(list, stringMap);

    if (contextData.containsKey(TRACE_ID)) {
      // Assume already instrumented event if traceId is present.
      return contextData;
    }

    SpanContext currentContext = TracingContextUtils.getCurrentSpan().getContext();
    if (!currentContext.isValid()) {
      return contextData;
    }

    StringMap newContextData = new SortedArrayStringMap(contextData);
    newContextData.putValue(TRACE_ID, currentContext.getTraceIdAsHexString());
    newContextData.putValue(SPAN_ID, currentContext.getSpanIdAsHexString());
    if (currentContext.isSampled()) {
      newContextData.putValue(SAMPLED, "true");
    }
    return newContextData;
  }

  @Override
  public ReadOnlyStringMap rawContextData() {
    return delegate.rawContextData();
  }
}

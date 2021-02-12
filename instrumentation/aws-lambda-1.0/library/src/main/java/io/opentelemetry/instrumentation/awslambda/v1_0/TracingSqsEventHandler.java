/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public abstract class TracingSqsEventHandler extends TracingRequestHandler<SQSEvent, Void> {

  private final AwsLambdaMessageTracer tracer;

  /** Creates a new {@link TracingRequestHandler} which traces using the default {@link Tracer}. */
  protected TracingSqsEventHandler() {
    this.tracer = new AwsLambdaMessageTracer();
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link Tracer}.
   */
  protected TracingSqsEventHandler(Tracer tracer) {
    super(tracer);
    this.tracer = new AwsLambdaMessageTracer(tracer);
  }

  /**
   * Creates a new {@link TracingRequestHandler} which traces using the specified {@link
   * AwsLambdaMessageTracer}.
   */
  protected TracingSqsEventHandler(AwsLambdaMessageTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Void doHandleRequest(SQSEvent event, Context context) {
    io.opentelemetry.context.Context otelContext = tracer.startSpan(event);
    Throwable error = null;
    try (Scope ignored = otelContext.makeCurrent()) {
      handleEvent(event, context);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      if (error != null) {
        tracer.endExceptionally(otelContext, error);
      } else {
        tracer.end(otelContext);
      }
      LambdaUtils.forceFlush();
    }
    return null;
  }

  /**
   * Handles a {@linkplain SQSEvent batch of messages}. Implement this class to do the actual
   * processing of incoming SQS messages.
   */
  protected abstract void handleEvent(SQSEvent event, Context context);

  // We use in SQS message handler too.
  AwsLambdaMessageTracer getTracer() {
    return tracer;
  }
}

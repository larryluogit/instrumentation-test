/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.asyncannotationsupport.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReactorAsyncOperationEndStrategy
    implements AsyncOperationEndStrategy, AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("reactor.canceled");

  public static ReactorAsyncOperationEndStrategy create() {
    return newBuilder().build();
  }

  public static ReactorAsyncOperationEndStrategyBuilder newBuilder() {
    return new ReactorAsyncOperationEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  ReactorAsyncOperationEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == Publisher.class || returnType == Mono.class || returnType == Flux.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {

    EndOnFirstNotificationConsumer notificationConsumer =
        new EndOnFirstNotificationConsumer(context) {
          @Override
          protected void end(Object result, Throwable error) {
            if (error == null) {
              tracer.end(context);
            } else {
              tracer.endExceptionally(context, error);
            }
          }
        };
    return end(returnValue, notificationConsumer);
  }

  @Override
  public <REQUEST, RESPONSE> Object end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    EndOnFirstNotificationConsumer notificationConsumer =
        new EndOnFirstNotificationConsumer(context) {
          @Override
          protected void end(Object result, Throwable error) {
            instrumenter.end(context, request, tryToGetResponse(responseType, result), error);
          }
        };
    return end(asyncValue, notificationConsumer);
  }

  private static Object end(
      Object asyncValue, EndOnFirstNotificationConsumer notificationConsumer) {

    if (asyncValue instanceof Mono) {
      Mono<?> mono = (Mono<?>) asyncValue;
      return mono.doOnError(notificationConsumer)
          .doOnSuccess(notificationConsumer::onSuccess)
          .doOnCancel(notificationConsumer::onCancel);
    } else {
      Flux<?> flux = Flux.from((Publisher<?>) asyncValue);
      return flux.doOnError(notificationConsumer)
          .doOnComplete(notificationConsumer)
          .doOnCancel(notificationConsumer::onCancel);
    }
  }

  @Nullable
  private static <RESPONSE> RESPONSE tryToGetResponse(Class<RESPONSE> responseType, Object result) {
    if (responseType.isInstance(result)) {
      return responseType.cast(result);
    }
    return null;
  }

  /**
   * Helper class to ensure that the span is ended exactly once regardless of how many OnComplete or
   * OnError notifications are received. Multiple notifications can happen anytime multiple
   * subscribers subscribe to the same publisher.
   */
  private abstract class EndOnFirstNotificationConsumer extends AtomicBoolean
      implements Runnable, Consumer<Throwable> {

    private final Context context;

    protected EndOnFirstNotificationConsumer(Context context) {
      super(false);
      this.context = context;
    }

    public <T> void onSuccess(T result) {
      accept(result, null);
    }

    public void onCancel() {
      if (compareAndSet(false, true)) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        end(null, null);
      }
    }

    @Override
    public void run() {
      accept(null, null);
    }

    @Override
    public void accept(Throwable exception) {
      end(null, exception);
    }

    private void accept(Object result, Throwable error) {
      if (compareAndSet(false, true)) {
        end(result, error);
      }
    }

    protected abstract void end(Object result, Throwable error);
  }
}

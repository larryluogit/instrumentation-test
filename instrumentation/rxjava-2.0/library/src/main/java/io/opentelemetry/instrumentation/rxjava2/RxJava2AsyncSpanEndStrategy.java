/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.parallel.ParallelFlowable;
import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Publisher;

public enum RxJava2AsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> returnType) {
    return returnType == Publisher.class
        || returnType == Completable.class
        || returnType == Maybe.class
        || returnType == Single.class
        || returnType == Observable.class
        || returnType == Flowable.class
        || returnType == ParallelFlowable.class;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    if (returnValue instanceof Completable) {
      return endWhenComplete(tracer, context, (Completable) returnValue);
    } else if (returnValue instanceof Maybe) {
      return endWhenMaybeComplete(tracer, context, (Maybe<?>) returnValue);
    } else if (returnValue instanceof Single) {
      return endWhenSingleComplete(tracer, context, (Single<?>) returnValue);
    } else if (returnValue instanceof Observable) {
      return endWhenObservableComplete(tracer, context, (Observable<?>) returnValue);
    } else if (returnValue instanceof ParallelFlowable) {
      return endWhenFirstComplete(tracer, context, (ParallelFlowable<?>) returnValue);
    }
    return endWhenPublisherComplete(tracer, context, (Publisher<?>) returnValue);
  }

  private Completable endWhenComplete(BaseTracer tracer, Context context, Completable completable) {

    EndOnFirstNotificationConsumer<?> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return completable.doOnEvent(notification);
  }

  private <T> Maybe<T> endWhenMaybeComplete(BaseTracer tracer, Context context, Maybe<T> maybe) {

    EndOnFirstNotificationConsumer<T> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return maybe.doOnEvent(notification);
  }

  private <T> Single<T> endWhenSingleComplete(
      BaseTracer tracer, Context context, Single<T> single) {

    EndOnFirstNotificationConsumer<T> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return single.doOnEvent(notification);
  }

  private <T> Observable<T> endWhenObservableComplete(
      BaseTracer tracer, Context context, Observable<T> observable) {

    EndOnFirstNotificationConsumer<?> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return observable.doOnComplete(notification).doOnError(notification);
  }

  private <T> ParallelFlowable<T> endWhenFirstComplete(
      BaseTracer tracer, Context context, ParallelFlowable<T> parallelFlowable) {

    EndOnFirstNotificationConsumer<?> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return parallelFlowable.doOnComplete(notification).doOnError(notification);
  }

  private <T> Flowable<T> endWhenPublisherComplete(
      BaseTracer tracer, Context context, Publisher<T> publisher) {

    EndOnFirstNotificationConsumer<?> notification =
        new EndOnFirstNotificationConsumer<>(tracer, context);
    return Flowable.fromPublisher(publisher).doOnComplete(notification).doOnError(notification);
  }

  /**
   * Helper class to ensure that the span is ended exactly once regardless of how many OnComplete or
   * OnError notifications are received. Multiple notifications can happen anytime multiple
   * subscribers subscribe to the same publisher.
   */
  private static final class EndOnFirstNotificationConsumer<T> extends AtomicBoolean
      implements Action, Consumer<Throwable>, BiConsumer<T, Throwable> {

    private final BaseTracer tracer;
    private final Context context;

    public EndOnFirstNotificationConsumer(BaseTracer tracer, Context context) {
      super(false);
      this.tracer = tracer;
      this.context = context;
    }

    @Override
    public void run() {
      if (compareAndSet(false, true)) {
        tracer.end(context);
      }
    }

    @Override
    public void accept(Throwable exception) {
      if (compareAndSet(false, true)) {
        if (exception != null) {
          tracer.endExceptionally(context, exception);
        } else {
          tracer.end(context);
        }
      }
    }

    @Override
    public void accept(T value, Throwable exception) {
      accept(exception);
    }
  }
}

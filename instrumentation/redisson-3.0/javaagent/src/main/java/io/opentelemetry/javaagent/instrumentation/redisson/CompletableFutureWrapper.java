/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.CompletableFuture;

public final class CompletableFutureWrapper<T> extends CompletableFuture<T>
    implements PromiseWrapper<T> {
  private volatile EndOperationListener<T> endOperationListener;

  private CompletableFutureWrapper(CompletableFuture<T> delegate) {
    this.whenComplete(
        (result, error) -> {
          EndOperationListener<T> endOperationListener = this.endOperationListener;
          if (endOperationListener != null) {
            endOperationListener.accept(result, error);
          }
          if (error != null) {
            delegate.completeExceptionally(error);
          } else {
            delegate.complete(result);
          }
        });
  }

  /**
   * Wrap {@link CompletableFuture} so that {@link EndOperationListener}, that is used to end the
   * span, could be attached to it.
   */
  public static <T> CompletableFuture<T> wrap(CompletableFuture<T> delegate) {
    if (delegate instanceof CompletableFutureWrapper) {
      return delegate;
    }

    return new CompletableFutureWrapper<>(delegate);
  }

  /**
   * Wrap {@link CompletableFuture} to run callbacks with the context that was current at the time
   * this method was called.
   *
   * <p>This method should be called on, or as close as possible to, the {@link CompletableFuture}
   * that is returned to the user to ensure that the callbacks added by user are run in appropriate
   * context.
   */
  public static <T> CompletableFuture<T> wrapContext(CompletableFuture<T> future) {
    Context context = Context.current();
    // when input future is completed, complete result future with context that was current
    // at the time when the future was wrapped
    CompletableFuture<T> result = new CompletableFuture<>();
    future.whenComplete(
        (T value, Throwable throwable) -> {
          try (Scope ignored = context.makeCurrent()) {
            if (throwable != null) {
              result.completeExceptionally(throwable);
            } else {
              result.complete(value);
            }
          }
        });

    return result;
  }

  @Override
  public void setEndOperationListener(EndOperationListener<T> endOperationListener) {
    this.endOperationListener = endOperationListener;
  }
}

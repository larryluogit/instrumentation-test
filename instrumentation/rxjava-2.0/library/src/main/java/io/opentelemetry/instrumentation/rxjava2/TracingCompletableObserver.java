/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.CompletableObserver;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;

class TracingCompletableObserver implements CompletableObserver, Disposable {

  private final CompletableObserver actual;
  private final Context parentSpan;
  private Disposable disposable;

  TracingCompletableObserver(final CompletableObserver actual, final Context parentSpan) {
    this.actual = actual;
    this.parentSpan = parentSpan;
  }

  @Override
  public void onSubscribe(final @NonNull Disposable d) {
    if (!DisposableHelper.validate(disposable, d)) {
      return;
    }
    disposable = d;
    actual.onSubscribe(this);
  }

  @Override
  public void onComplete() {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onComplete();
    }
  }

  @Override
  public void onError(final @NonNull Throwable e) {
    try (Scope ignored = parentSpan.makeCurrent()) {
      actual.onError(e);
    }
  }

  @Override
  public void dispose() {
    disposable.dispose();
  }

  @Override
  public boolean isDisposed() {
    return disposable.isDisposed();
  }
}

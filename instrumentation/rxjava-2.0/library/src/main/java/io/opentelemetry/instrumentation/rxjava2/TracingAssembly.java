/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.opentelemetry.instrumentation.rxjava2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.internal.fuseable.ConditionalSubscriber;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.parallel.ParallelFlowable;
import io.reactivex.plugins.RxJavaPlugins;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.reactivestreams.Subscriber;

/**
 * RxJava2 library instrumentation.
 *
 * <p>In order to enable RxJava2 instrumentation one has to call the <code>TracingAssembly.enable()
 * </code> method.
 *
 * <p>Instrumentation uses <code>on*Assembly</code> and <code>on*Subscribe</code> RxJavaPlugin hooks
 * to wrap RxJava2 classes in their tracing equivalents.
 *
 * <p>Instrumentation can be disabled by calling the <code>TracingAssembly.disable()</code> method.
 */
public final class TracingAssembly {

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Observable, ? super Observer, ? extends Observer>
      oldOnObservableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ConnectableObservable, ? extends ConnectableObservable>
      oldOnConnectableObservableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<
          ? super Completable, ? super CompletableObserver, ? extends CompletableObserver>
      oldOnCompletableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Single, ? super SingleObserver, ? extends SingleObserver>
      oldOnSingleSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Maybe, ? super MaybeObserver, ? extends MaybeObserver>
      oldOnMaybeSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static BiFunction<? super Flowable, ? super Subscriber, ? extends Subscriber>
      oldOnFlowableSubscribe;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ConnectableFlowable, ? extends ConnectableFlowable>
      oldOnConnectableFlowableAssembly;

  @SuppressWarnings("rawtypes")
  @GuardedBy("TracingAssembly.class")
  private static Function<? super ParallelFlowable, ? extends ParallelFlowable>
      oldOnParallelAssembly;

  @GuardedBy("TracingAssembly.class")
  private static boolean enabled;

  private TracingAssembly() {}

  public static synchronized void enable() {
    if (enabled) {
      return;
    }

    enableObservable();

    enableConnectableObservable();

    enableCompletable();

    enableSingle();

    enableMaybe();

    enableFlowable();

    enableConnectableFlowable();

    enableParallel();

    enabled = true;
  }

  public static synchronized void disable() {
    if (!enabled) {
      return;
    }

    disableSingle();

    disableMaybe();

    disableFlowable();

    disableParallel();

    disableObservable();

    disableConnectableObservable();

    disableCompletable();

    disableConnectableFlowable();

    enabled = false;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableParallel() {
    oldOnParallelAssembly = RxJavaPlugins.getOnParallelAssembly();
    RxJavaPlugins.setOnParallelAssembly(
        compose(
            oldOnParallelAssembly,
            parallelFlowable -> new TracingParallelFlowable(parallelFlowable, Context.current())));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableConnectableFlowable() {
    oldOnConnectableFlowableAssembly = RxJavaPlugins.getOnConnectableFlowableAssembly();
    RxJavaPlugins.setOnConnectableFlowableAssembly(
        compose(
            oldOnConnectableFlowableAssembly,
            connectableFlowable ->
                new TracingConnectableFlowable(connectableFlowable, Context.current())));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableConnectableObservable() {
    oldOnConnectableObservableAssembly = RxJavaPlugins.getOnConnectableObservableAssembly();
    RxJavaPlugins.setOnConnectableObservableAssembly(
        compose(
            oldOnConnectableObservableAssembly,
            connectableObservable ->
                new TracingConnectableObservable(connectableObservable, Context.current())));
  }

  private static void enableCompletable() {
    oldOnCompletableSubscribe = RxJavaPlugins.getOnCompletableSubscribe();
    RxJavaPlugins.setOnCompletableSubscribe(
        biCompose(
            oldOnCompletableSubscribe,
            (completable, observer) -> {
              final Context context = Context.current();
              try (Scope ignored = context.makeCurrent()) {
                return new TracingCompletableObserver(observer, context);
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableFlowable() {
    oldOnFlowableSubscribe = RxJavaPlugins.getOnFlowableSubscribe();
    RxJavaPlugins.setOnFlowableSubscribe(
        biCompose(
            oldOnFlowableSubscribe,
            (flowable, subscriber) -> {
              final Context context = Context.current();
              try (Scope ignored = context.makeCurrent()) {
                if (subscriber instanceof ConditionalSubscriber) {
                  return new TracingConditionalSubscriber<>(
                      (ConditionalSubscriber) subscriber, context);
                } else {
                  return new TracingSubscriber<>(subscriber, context);
                }
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableObservable() {
    oldOnObservableSubscribe = RxJavaPlugins.getOnObservableSubscribe();
    RxJavaPlugins.setOnObservableSubscribe(
        biCompose(
            oldOnObservableSubscribe,
            (observable, observer) -> {
              final Context context = Context.current();
              try (Scope ignored = context.makeCurrent()) {
                return new TracingObserver(observer, context);
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableSingle() {
    oldOnSingleSubscribe = RxJavaPlugins.getOnSingleSubscribe();
    RxJavaPlugins.setOnSingleSubscribe(
        biCompose(
            oldOnSingleSubscribe,
            (single, singleObserver) -> {
              final Context context = Context.current();
              try (Scope ignored = context.makeCurrent()) {
                return new TracingSingleObserver(singleObserver, context);
              }
            }));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void enableMaybe() {
    oldOnMaybeSubscribe = RxJavaPlugins.getOnMaybeSubscribe();
    RxJavaPlugins.setOnMaybeSubscribe(
        (BiFunction<? super Maybe, MaybeObserver, ? extends MaybeObserver>)
            biCompose(
                oldOnMaybeSubscribe,
                (BiFunction<Maybe, MaybeObserver, MaybeObserver>)
                    (maybe, maybeObserver) -> {
                      final Context context = Context.current();
                      try (Scope ignored = context.makeCurrent()) {
                        return new TracingMaybeObserver(maybeObserver, context);
                      }
                    }));
  }

  private static void disableConnectableFlowable() {
    RxJavaPlugins.setOnConnectableFlowableAssembly(oldOnConnectableFlowableAssembly);
    oldOnConnectableFlowableAssembly = null;
  }

  private static void disableConnectableObservable() {
    RxJavaPlugins.setOnConnectableObservableAssembly(oldOnConnectableObservableAssembly);
    oldOnConnectableObservableAssembly = null;
  }

  private static void disableParallel() {
    RxJavaPlugins.setOnParallelAssembly(oldOnParallelAssembly);
    oldOnParallelAssembly = null;
  }

  private static void disableObservable() {
    RxJavaPlugins.setOnObservableSubscribe(oldOnObservableSubscribe);
    oldOnObservableSubscribe = null;
  }

  private static void disableCompletable() {
    RxJavaPlugins.setOnCompletableSubscribe(oldOnCompletableSubscribe);
    oldOnCompletableSubscribe = null;
  }

  private static void disableFlowable() {
    RxJavaPlugins.setOnFlowableSubscribe(oldOnFlowableSubscribe);
    oldOnFlowableSubscribe = null;
  }

  private static void disableSingle() {
    RxJavaPlugins.setOnSingleSubscribe(oldOnSingleSubscribe);
    oldOnSingleSubscribe = null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void disableMaybe() {
    RxJavaPlugins.setOnMaybeSubscribe(
        (BiFunction<? super Maybe, MaybeObserver, ? extends MaybeObserver>) oldOnMaybeSubscribe);
    oldOnMaybeSubscribe = null;
  }

  private static <T> Function<? super T, ? extends T> compose(
      Function<? super T, ? extends T> before, Function<? super T, ? extends T> after) {
    if (before == null) {
      return after;
    }
    return (T v) -> after.apply(before.apply(v));
  }

  private static <T, U> BiFunction<? super T, ? super U, ? extends U> biCompose(
      BiFunction<? super T, ? super U, ? extends U> before,
      BiFunction<? super T, ? super U, ? extends U> after) {
    if (before == null) {
      return after;
    }
    return (T v, U u) -> after.apply(v, before.apply(v, u));
  }
}

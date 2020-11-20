/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.util.gc;

import java.lang.ref.WeakReference;

public abstract class GcUtils {

  public static void awaitGc() throws InterruptedException {
    Object obj = new Object();
    WeakReference<Object> ref = new WeakReference<>(obj);
    obj = null;
    awaitGc(ref);
  }

  public static void awaitGc(WeakReference<?> ref) throws InterruptedException {
    while (ref.get() != null) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      System.gc();
      System.runFinalization();
    }
  }
}

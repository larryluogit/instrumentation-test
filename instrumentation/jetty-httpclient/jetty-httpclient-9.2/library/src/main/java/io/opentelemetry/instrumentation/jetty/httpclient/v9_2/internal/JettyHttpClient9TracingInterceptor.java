/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * JettyHttpClient9TracingInterceptor does three jobs stimulated from the Jetty Request object from
 * attachToRequest() 1. Start the CLIENT span and create the tracer 2. Set the listener callbacks
 * for each important lifecycle actions that would cause the start and close of the span 3. Set
 * callback wrappers on two important request-based callbacks
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JettyHttpClient9TracingInterceptor
    implements Request.BeginListener,
        Request.FailureListener,
        Response.SuccessListener,
        Response.FailureListener {

  private static final Logger logger =
      Logger.getLogger(JettyHttpClient9TracingInterceptor.class.getName());

  private static final Class<?>[] requestlistenerInterfaces = {
    Request.BeginListener.class,
    Request.FailureListener.class,
    Request.SuccessListener.class,
    Request.HeadersListener.class,
    Request.ContentListener.class,
    Request.CommitListener.class,
    Request.QueuedListener.class
  };

  @Nullable private Context context;

  @Nullable
  public Context getContext() {
    return this.context;
  }

  private final Context parentContext;

  private final Instrumenter<Request, Response> instrumenter;

  public JettyHttpClient9TracingInterceptor(
      Context parentCtx, Instrumenter<Request, Response> instrumenter) {
    this.parentContext = parentCtx;
    this.instrumenter = instrumenter;
  }

  public void attachToRequest(Request jettyRequest) {
    List<JettyHttpClient9TracingInterceptor> current =
        jettyRequest.getRequestListeners(JettyHttpClient9TracingInterceptor.class);

    if (!current.isEmpty()) {
      logger.warning("A tracing interceptor is already in place for this request!");
      return;
    }
    startSpan(jettyRequest);

    // wrap all important request-based listeners that may already be attached, null should ensure
    // are returned here
    List<Request.RequestListener> existingListeners = jettyRequest.getRequestListeners(null);
    wrapRequestListeners(existingListeners);

    jettyRequest
        .onRequestBegin(this)
        .onRequestFailure(this)
        .onResponseFailure(this)
        .onResponseSuccess(this);
  }

  private void wrapRequestListeners(List<Request.RequestListener> requestListeners) {
    ListIterator<Request.RequestListener> iterator = requestListeners.listIterator();

    while (iterator.hasNext()) {
      List<Class<?>> interfaces = new ArrayList<>();
      Request.RequestListener listener = iterator.next();

      Class<?> listenerClass = listener.getClass();

      for (Class<?> type : requestlistenerInterfaces) {
        if (type.isInstance(listener)) {
          interfaces.add(type);
        }
      }

      if (interfaces.isEmpty()) {
        continue;
      }

      Request.RequestListener proxiedListener =
          (Request.RequestListener)
              Proxy.newProxyInstance(
                  listenerClass.getClassLoader(),
                  interfaces.toArray(new Class<?>[0]),
                  (proxy, method, args) -> {
                    try (Scope ignored = context.makeCurrent()) {
                      return method.invoke(listener, args);
                    } catch (InvocationTargetException exception) {
                      throw exception.getCause();
                    }
                  });

      iterator.set(proxiedListener);
    }
  }

  private void startSpan(Request request) {
    if (!instrumenter.shouldStart(this.parentContext, request)) {
      return;
    }
    this.context = instrumenter.start(this.parentContext, request);
  }

  @Override
  public void onBegin(Request request) {}

  @Override
  public void onSuccess(Response response) {
    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, null);
    }
  }

  @Override
  public void onFailure(Request request, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, request, null, t);
    }
  }

  @Override
  public void onFailure(Response response, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, t);
    }
  }
}

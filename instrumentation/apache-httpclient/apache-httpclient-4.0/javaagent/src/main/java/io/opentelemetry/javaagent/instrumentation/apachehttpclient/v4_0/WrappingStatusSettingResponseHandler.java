/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;

public class WrappingStatusSettingResponseHandler<T> implements ResponseHandler<T> {
  final Operation operation;
  final ResponseHandler<T> handler;

  public static <T> WrappingStatusSettingResponseHandler<T> of(
      Operation operation, ResponseHandler<T> handler) {
    return new WrappingStatusSettingResponseHandler<>(operation, handler);
  }

  public WrappingStatusSettingResponseHandler(Operation operation, ResponseHandler<T> handler) {
    this.operation = operation;
    this.handler = handler;
  }

  @Override
  public T handleResponse(HttpResponse response) throws IOException {
    // TODO (trask) suppress second call to end
    tracer().end(operation, response);
    return handler.handleResponse(response);
  }
}

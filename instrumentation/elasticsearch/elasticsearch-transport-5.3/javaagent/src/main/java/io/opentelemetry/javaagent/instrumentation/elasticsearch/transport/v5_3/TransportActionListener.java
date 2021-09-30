/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.Elasticsearch53TransportSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;

public class TransportActionListener<T extends ActionResponse> implements ActionListener<T> {

  private final ElasticTransportRequest actionRequest;
  private final ActionListener<T> listener;
  private final Context context;
  private final Context parentContext;

  public TransportActionListener(
      ElasticTransportRequest actionRequest,
      ActionListener<T> listener,
      Context context,
      Context parentContext) {
    this.actionRequest = actionRequest;
    this.listener = listener;
    this.context = context;
    this.parentContext = parentContext;
  }

  @Override
  public void onResponse(T response) {
    instrumenter().end(context, actionRequest, response, null);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onResponse(response);
    }
  }

  @Override
  public void onFailure(Exception e) {
    instrumenter().end(context, actionRequest, null, e);
    try (Scope ignored = parentContext.makeCurrent()) {
      listener.onFailure(e);
    }
  }
}

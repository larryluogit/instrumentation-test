/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import java.net.InetSocketAddress;
import java.util.List;
import play.shaded.ahc.io.netty.channel.Channel;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;
import play.shaded.ahc.org.asynchttpclient.netty.request.NettyRequest;

public class AsyncHandlerWrapper<T> implements AsyncHandler<T> {
  private final AsyncHandler<T> delegate;
  private final HttpClientOperation<Response> operation;

  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

  public AsyncHandlerWrapper(AsyncHandler<T> delegate, HttpClientOperation<Response> operation) {
    this.delegate = delegate;
    this.operation = operation;
  }

  @Override
  public State onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
    builder.accumulate(content);
    return delegate.onBodyPartReceived(content);
  }

  @Override
  public State onStatusReceived(HttpResponseStatus status) throws Exception {
    builder.reset();
    builder.accumulate(status);
    return delegate.onStatusReceived(status);
  }

  @Override
  public State onHeadersReceived(HttpHeaders httpHeaders) throws Exception {
    builder.accumulate(httpHeaders);
    return delegate.onHeadersReceived(httpHeaders);
  }

  @Override
  public T onCompleted() throws Exception {
    Response response = builder.build();
    operation.end(response);
    try (Scope ignored = operation.makeParentCurrent()) {
      return delegate.onCompleted();
    }
  }

  @Override
  public void onThrowable(Throwable throwable) {
    operation.endExceptionally(throwable);
    try (Scope ignored = operation.makeParentCurrent()) {
      delegate.onThrowable(throwable);
    }
  }

  @Override
  public State onTrailingHeadersReceived(HttpHeaders headers) throws Exception {
    return delegate.onTrailingHeadersReceived(headers);
  }

  @Override
  public void onHostnameResolutionAttempt(String name) {
    delegate.onHostnameResolutionAttempt(name);
  }

  @Override
  public void onHostnameResolutionSuccess(String name, List<InetSocketAddress> list) {
    delegate.onHostnameResolutionSuccess(name, list);
  }

  @Override
  public void onHostnameResolutionFailure(String name, Throwable cause) {
    delegate.onHostnameResolutionFailure(name, cause);
  }

  @Override
  public void onTcpConnectAttempt(InetSocketAddress remoteAddress) {
    delegate.onTcpConnectAttempt(remoteAddress);
  }

  @Override
  public void onTcpConnectSuccess(InetSocketAddress remoteAddress, Channel connection) {
    delegate.onTcpConnectSuccess(remoteAddress, connection);
  }

  @Override
  public void onTcpConnectFailure(InetSocketAddress remoteAddress, Throwable cause) {
    delegate.onTcpConnectFailure(remoteAddress, cause);
  }

  @Override
  public void onTlsHandshakeAttempt() {
    delegate.onTlsHandshakeAttempt();
  }

  @Override
  public void onTlsHandshakeSuccess() {
    delegate.onTlsHandshakeSuccess();
  }

  @Override
  public void onTlsHandshakeFailure(Throwable cause) {
    delegate.onTlsHandshakeFailure(cause);
  }

  @Override
  public void onConnectionPoolAttempt() {
    delegate.onConnectionPoolAttempt();
  }

  @Override
  public void onConnectionPooled(Channel connection) {
    delegate.onConnectionPooled(connection);
  }

  @Override
  public void onConnectionOffer(Channel connection) {
    delegate.onConnectionOffer(connection);
  }

  @Override
  public void onRequestSend(NettyRequest request) {
    delegate.onRequestSend(request);
  }

  @Override
  public void onRetry() {
    delegate.onRetry();
  }
}

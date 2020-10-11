/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_5.server;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.instrumentation.grpc.v1_5.common.GrpcHelper;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

public class TracingServerInterceptor implements ServerInterceptor {

  public static ServerInterceptor newInterceptor() {
    return newInterceptor(new GrpcServerTracer());
  }

  public static ServerInterceptor newInterceptor(Tracer tracer) {
    return newInterceptor(new GrpcServerTracer(tracer));
  }

  public static ServerInterceptor newInterceptor(GrpcServerTracer tracer) {
    return new TracingServerInterceptor(tracer);
  }

  private final GrpcServerTracer tracer;

  private TracingServerInterceptor(GrpcServerTracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    Span span = tracer.startSpan(methodName, headers);

    SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
    if (address instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) address;
      span.setAttribute(SemanticAttributes.NET_PEER_PORT, inetSocketAddress.getPort());
      span.setAttribute(
          SemanticAttributes.NET_PEER_IP, inetSocketAddress.getAddress().getHostAddress());
    }
    GrpcHelper.prepareSpan(span, methodName);

    Context context = TracingContextUtils.withSpan(span, Context.current());

    try {
      return new TracingServerCallListener<>(
          Contexts.interceptCall(
              context, new TracingServerCall<>(call, span, tracer), headers, next),
          span,
          tracer);
    } catch (Throwable e) {
      tracer.endExceptionally(span, e);
      throw e;
    }
  }

  static final class TracingServerCall<ReqT, RespT>
      extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {
    private final Span span;
    private final GrpcServerTracer tracer;

    TracingServerCall(ServerCall<ReqT, RespT> delegate, Span span, GrpcServerTracer tracer) {
      super(delegate);
      this.span = span;
      this.tracer = tracer;
    }

    @Override
    public void close(Status status, Metadata trailers) {
      tracer.setStatus(span, status);
      try {
        delegate().close(status, trailers);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }

  static final class TracingServerCallListener<ReqT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final Span span;
    private final GrpcServerTracer tracer;

    private final AtomicLong messageId = new AtomicLong();

    TracingServerCallListener(Listener<ReqT> delegate, Span span, GrpcServerTracer tracer) {
      super(delegate);
      this.span = span;
      this.tracer = tracer;
    }

    @Override
    public void onMessage(ReqT message) {
      Attributes attributes =
          Attributes.of(
              SemanticAttributes.GRPC_MESSAGE_TYPE,
              "RECEIVED",
              SemanticAttributes.GRPC_MESSAGE_ID,
              messageId.incrementAndGet());
      span.addEvent("message", attributes);
      delegate().onMessage(message);
    }

    @Override
    public void onHalfClose() {
      try {
        delegate().onHalfClose();
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }

    @Override
    public void onCancel() {
      try {
        delegate().onCancel();
        span.setAttribute("canceled", true);
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
      tracer.end(span);
    }

    @Override
    public void onComplete() {
      try {
        delegate().onComplete();
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
      tracer.end(span);
    }

    @Override
    public void onReady() {
      try {
        delegate().onReady();
      } catch (Throwable e) {
        tracer.endExceptionally(span, e);
        throw e;
      }
    }
  }
}

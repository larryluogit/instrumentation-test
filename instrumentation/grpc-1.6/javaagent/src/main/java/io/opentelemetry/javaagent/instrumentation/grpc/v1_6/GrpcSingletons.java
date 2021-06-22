/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTracing;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTracingBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;

// Holds singleton references.
public final class GrpcSingletons {

  public static final ClientInterceptor CLIENT_INTERCEPTOR;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  public static final Context.Storage STORAGE = new ContextStorageBridge();

  static {
    GrpcTracingBuilder builder =
        GrpcTracing.newBuilder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(
                Config.get()
                    .getBooleanProperty(
                        "otel.instrumentation.grpc.experimental-span-attributes", false));

    PeerServiceAttributesExtractor<GrpcRequest, Status> peerServiceExtractor =
        PeerServiceAttributesExtractor.createUsingReflection(
            "io.opentelemetry.instrumentation.grpc.v1_6.GrpcNetAttributesExtractor");
    if (peerServiceExtractor != null) {
      builder.addAttributeExtractor(peerServiceExtractor);
    }

    GrpcTracing tracing = builder.build();
    CLIENT_INTERCEPTOR = tracing.newClientInterceptor();
    SERVER_INTERCEPTOR = tracing.newServerInterceptor();
  }

  private GrpcSingletons() {}
}

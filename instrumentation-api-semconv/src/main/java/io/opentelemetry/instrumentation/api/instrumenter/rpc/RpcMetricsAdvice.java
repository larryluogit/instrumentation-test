/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;

final class RpcMetricsAdvice {

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    // and
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/rpc-metrics.md
    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.RPC_SYSTEM);
    attributes.add(SemanticAttributes.RPC_SERVICE);
    attributes.add(SemanticAttributes.RPC_METHOD);
    attributes.add(SemanticAttributes.RPC_GRPC_STATUS_CODE);
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(NetworkAttributes.NETWORK_TYPE);
      attributes.add(NetworkAttributes.NETWORK_TRANSPORT);
      attributes.add(NetworkAttributes.SERVER_ADDRESS);
      attributes.add(NetworkAttributes.SERVER_PORT);
      attributes.add(NetworkAttributes.SERVER_SOCKET_ADDRESS);
      attributes.add(NetworkAttributes.SERVER_SOCKET_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_PEER_NAME);
      attributes.add(SemanticAttributes.NET_PEER_PORT);
      attributes.add(SemanticAttributes.NET_TRANSPORT);
    }

    ((ExtendedDoubleHistogramBuilder) builder)
        .setAdvice(advice -> advice.setAttributes(attributes));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    // the list of recommended metrics attributes is from
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/rpc/rpc-metrics.md
    // and
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/rpc-metrics.md
    List<AttributeKey<?>> attributes = new ArrayList<>();
    attributes.add(SemanticAttributes.RPC_SYSTEM);
    attributes.add(SemanticAttributes.RPC_SERVICE);
    attributes.add(SemanticAttributes.RPC_METHOD);
    attributes.add(SemanticAttributes.RPC_GRPC_STATUS_CODE);
    if (SemconvStability.emitStableHttpSemconv()) {
      attributes.add(NetworkAttributes.NETWORK_TYPE);
      attributes.add(NetworkAttributes.NETWORK_TRANSPORT);
      attributes.add(NetworkAttributes.SERVER_ADDRESS);
      attributes.add(NetworkAttributes.SERVER_PORT);
      attributes.add(NetworkAttributes.SERVER_SOCKET_ADDRESS);
      attributes.add(NetworkAttributes.SERVER_SOCKET_PORT);
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      attributes.add(SemanticAttributes.NET_HOST_NAME);
      attributes.add(SemanticAttributes.NET_SOCK_HOST_ADDR);
      attributes.add(SemanticAttributes.NET_TRANSPORT);
    }

    ((ExtendedDoubleHistogramBuilder) builder)
        .setAdvice(advice -> advice.setAttributes(attributes));
  }

  private RpcMetricsAdvice() {}
}

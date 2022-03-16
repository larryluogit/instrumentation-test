/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/rpc.md">RPC
 * server attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link RpcCommonAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class RpcServerAttributesExtractor<REQUEST, RESPONSE>
    extends RpcCommonAttributesExtractor<REQUEST, RESPONSE> {

  /** Creates the RPC server attributes extractor. */
  public static <REQUEST, RESPONSE> RpcServerAttributesExtractor<REQUEST, RESPONSE> create(
      RpcServerAttributesGetter<REQUEST> getter) {
    return new RpcServerAttributesExtractor<>(getter);
  }

  private RpcServerAttributesExtractor(RpcCommonAttributesGetter<REQUEST> getter) {
    super(getter);
  }
}

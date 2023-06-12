/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.FallbackNamePortGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.InternalNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalNetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.InternalServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkTransportFilter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-network-connection-attributes">Network
 * attributes</a>.
 */
public final class NetServerAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      NetServerAttributesGetter<REQUEST, RESPONSE> getter) {
    return new NetServerAttributesExtractor<>(getter);
  }

  private final InternalNetServerAttributesExtractor<REQUEST, RESPONSE> internalExtractor;
  private final InternalNetworkAttributesExtractor<REQUEST, RESPONSE> internalNetworkExtractor;
  private final InternalServerAttributesExtractor<REQUEST, RESPONSE> internalServerExtractor;

  private NetServerAttributesExtractor(NetServerAttributesGetter<REQUEST, RESPONSE> getter) {
    internalExtractor =
        new InternalNetServerAttributesExtractor<>(
            getter, FallbackNamePortGetter.noop(), SemconvStability.emitOldHttpSemconv());
    internalNetworkExtractor =
        new InternalNetworkAttributesExtractor<>(
            getter,
            NetworkTransportFilter.alwaysTrue(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv());
    internalServerExtractor =
        new InternalServerAttributesExtractor<>(
            getter,
            (port, request) -> true,
            FallbackNamePortGetter.noop(),
            SemconvStability.emitStableHttpSemconv(),
            SemconvStability.emitOldHttpSemconv(),
            InternalServerAttributesExtractor.Mode.HOST);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalExtractor.onStart(attributes, request);
    internalServerExtractor.onStart(attributes, request);
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalNetworkExtractor.onEnd(attributes, request, response);
    internalServerExtractor.onEnd(attributes, request, response);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Extractor of the {@code peer.service} span attribute, described in <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/span-general.md#general-remote-service-attributes">the
 * specification</a>.
 */
public final class PeerServiceAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter;
  private final Map<String, String> peerServiceMapping;

  // visible for tests
  PeerServiceAttributesExtractor(
      ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      Map<String, String> peerServiceMapping) {
    this.attributesGetter = attributesGetter;
    this.peerServiceMapping = peerServiceMapping;
  }

  /**
   * Returns a new {@link PeerServiceAttributesExtractor} that will use the passed {@code
   * netAttributesExtractor} instance to determine the value of the {@code peer.service} attribute.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      ServerAttributesGetter<REQUEST, RESPONSE> attributesGetter,
      Map<String, String> peerServiceMapping) {
    return new PeerServiceAttributesExtractor<>(attributesGetter, peerServiceMapping);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {

    if (peerServiceMapping.isEmpty()) {
      // optimization for common case
      return;
    }

    String serverAddress = attributesGetter.getServerAddress(request);
    String peerService = mapToPeerService(serverAddress);
    if (peerService == null && SemconvStability.emitOldHttpSemconv()) {
      String serverSocketDomain = attributesGetter.getServerSocketDomain(request, response);
      peerService = mapToPeerService(serverSocketDomain);
    }
    if (peerService != null) {
      attributes.put(SemanticAttributes.PEER_SERVICE, peerService);
    }
  }

  @Nullable
  private String mapToPeerService(@Nullable String endpoint) {
    if (endpoint == null) {
      return null;
    }
    return peerServiceMapping.get(endpoint);
  }
}

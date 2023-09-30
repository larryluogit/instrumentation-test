/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PeerServiceAttributesExtractorTest {
  @Mock ServerAttributesGetter<String, String> netAttributesExtractor;

  @Test
  void shouldNotSetAnyValueIfNetExtractorReturnsNulls() {
    // given
    Map<String, String> peerServiceMapping = singletonMap("1.2.3.4", "myService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(netAttributesExtractor, peerServiceMapping);

    Context context = Context.root();

    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, context, "request");
    underTest.onEnd(attributes, context, "request", "response", null);

    // then
    assertTrue(attributes.build().isEmpty());
  }

  @Test
  void shouldNotSetAnyValueIfPeerNameDoesNotMatch() {
    // given
    Map<String, String> peerServiceMapping = singletonMap("example.com", "myService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(netAttributesExtractor, peerServiceMapping);

    when(netAttributesExtractor.getServerAddress(any())).thenReturn("example2.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertTrue(startAttributes.build().isEmpty());
    assertTrue(endAttributes.build().isEmpty());
  }

  @Test
  void shouldSetPeerNameIfItMatches() {
    // given
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com", "myService");
    peerServiceMapping.put("1.2.3.4", "someOtherService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(netAttributesExtractor, peerServiceMapping);

    when(netAttributesExtractor.getServerAddress(any())).thenReturn("example.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "myService"));
    verify(netAttributesExtractor, never()).getServerSocketDomain(any(), any());
  }

  @Test
  void shouldSetSockPeerNameIfItMatchesAndNoPeerNameProvided() {

    Assumptions.assumeTrue(SemconvStability.emitOldHttpSemconv());

    // given
    Map<String, String> peerServiceMapping = new HashMap<>();
    peerServiceMapping.put("example.com", "myService");
    peerServiceMapping.put("1.2.3.4", "someOtherService");

    PeerServiceAttributesExtractor<String, String> underTest =
        new PeerServiceAttributesExtractor<>(netAttributesExtractor, peerServiceMapping);

    when(netAttributesExtractor.getServerSocketDomain(any(), any())).thenReturn("example.com");

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, "request");
    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, "request", "response", null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.PEER_SERVICE, "myService"));
  }
}

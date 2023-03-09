/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.function.BiPredicate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InternalNetServerAttributesExtractor<REQUEST> {

  private final NetServerAttributesGetter<REQUEST> getter;
  private final BiPredicate<Integer, REQUEST> captureHostPortCondition;
  private final FallbackNamePortGetter<REQUEST> fallbackNamePortGetter;

  public InternalNetServerAttributesExtractor(
      NetServerAttributesGetter<REQUEST> getter,
      BiPredicate<Integer, REQUEST> captureHostPortCondition,
      FallbackNamePortGetter<REQUEST> fallbackNamePortGetter) {
    this.getter = getter;
    this.captureHostPortCondition = captureHostPortCondition;
    this.fallbackNamePortGetter = fallbackNamePortGetter;
  }

  public void onStart(AttributesBuilder attributes, REQUEST request) {

    internalSet(attributes, SemanticAttributes.NET_TRANSPORT, getter.getTransport(request));
    internalSet(
        attributes, SemanticAttributes.NET_APP_PROTOCOL_NAME, getter.getProtocolName(request));
    internalSet(
        attributes,
        SemanticAttributes.NET_APP_PROTOCOL_VERSION,
        getter.getProtocolVersion(request));

    boolean setSockFamily = false;

    String sockPeerAddr = getter.getSockPeerAddr(request);
    if (sockPeerAddr != null) {
      setSockFamily = true;

      internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_ADDR, sockPeerAddr);

      Integer sockPeerPort = getter.getSockPeerPort(request);
      if (sockPeerPort != null && sockPeerPort > 0) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_PEER_PORT, (long) sockPeerPort);
      }
    }

    String hostName = extractHostName(request);
    Integer hostPort = extractHostPort(request);

    if (hostName != null) {
      internalSet(attributes, SemanticAttributes.NET_HOST_NAME, hostName);

      if (hostPort != null && hostPort > 0 && captureHostPortCondition.test(hostPort, request)) {
        internalSet(attributes, SemanticAttributes.NET_HOST_PORT, (long) hostPort);
      }
    }

    String sockHostAddr = getter.getSockHostAddr(request);
    if (sockHostAddr != null && !sockHostAddr.equals(hostName)) {
      setSockFamily = true;

      internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_ADDR, sockHostAddr);

      Integer sockHostPort = getter.getSockHostPort(request);
      if (sockHostPort != null && sockHostPort > 0 && !sockHostPort.equals(hostPort)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_HOST_PORT, (long) sockHostPort);
      }
    }

    if (setSockFamily) {
      String sockFamily = getter.getSockFamily(request);
      if (sockFamily != null && !SemanticAttributes.NetSockFamilyValues.INET.equals(sockFamily)) {
        internalSet(attributes, SemanticAttributes.NET_SOCK_FAMILY, sockFamily);
      }
    }
  }

  private String extractHostName(REQUEST request) {
    String peerName = getter.getHostName(request);
    if (peerName == null) {
      peerName = fallbackNamePortGetter.name(request);
    }
    return peerName;
  }

  private Integer extractHostPort(REQUEST request) {
    Integer peerPort = getter.getHostPort(request);
    if (peerPort == null) {
      peerPort = fallbackNamePortGetter.port(request);
    }
    return peerPort;
  }
}

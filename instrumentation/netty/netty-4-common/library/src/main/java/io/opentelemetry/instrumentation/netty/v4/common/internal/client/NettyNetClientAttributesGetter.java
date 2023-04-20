/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  public String getTransport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getProtocolName(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.request().getProtocolVersion().protocolName();
  }

  @Override
  public String getProtocolVersion(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    HttpVersion version = requestAndChannel.request().getProtocolVersion();
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Nullable
  @Override
  public String getPeerName(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}

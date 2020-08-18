/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class NetPeerHelper {

  public static void onPeerConnection(Span span, InetSocketAddress remoteConnection) {
    if (remoteConnection != null) {
      InetAddress remoteAddress = remoteConnection.getAddress();
      if (remoteAddress != null) {
        onPeerConnection(span, remoteAddress);
      } else {
        // Failed DNS lookup, the host string is the name.
        setPeer(span, remoteConnection.getHostString(), null);
      }
      span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), remoteConnection.getPort());
    }
  }

  public static void onPeerConnection(Span span, InetAddress remoteAddress) {
    setPeer(span, remoteAddress.getHostName(), remoteAddress.getHostAddress());
  }

  public static void setPeer(Span span, String peerName, String peerIp) {
    if (peerName != null && !peerName.equals(peerIp)) {
      SemanticAttributes.NET_PEER_NAME.set(span, peerName);
    }
    if (peerIp != null) {
      SemanticAttributes.NET_PEER_IP.set(span, peerIp);
    }
    String peerService = mapToPeer(peerName);
    if (peerService == null) {
      peerService = mapToPeer(peerIp);
    }
    if (peerService != null) {
      SemanticAttributes.PEER_SERVICE.set(span, peerService);
    }
  }

  private static String mapToPeer(String endpoint) {
    if (endpoint == null) {
      return null;
    }

    return Config.get().getEndpointPeerServiceMapping().get(endpoint);
  }
}

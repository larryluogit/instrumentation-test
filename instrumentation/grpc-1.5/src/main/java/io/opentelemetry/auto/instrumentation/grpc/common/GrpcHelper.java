/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.grpc.common;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public class GrpcHelper {
  public static void prepareSpan(
      final Span span,
      final String methodName,
      final InetSocketAddress peerAddress,
      final boolean server) {
    String serviceName =
        "(unknown)"; // Spec says it's mandatory, so populate even if we couldn't determine it.
    final int slash = methodName.indexOf('/');
    if (slash != -1) {
      final String fullServiceName = methodName.substring(0, slash);
      final int dot = fullServiceName.lastIndexOf('.');
      if (dot != -1) {
        serviceName = fullServiceName.substring(dot + 1);
      }
    }
    span.setAttribute(MoreTags.RPC_SERVICE, serviceName);
    if (peerAddress != null) {
      span.setAttribute(MoreTags.NET_PEER_PORT, peerAddress.getPort());
      if (server) {
        span.setAttribute(MoreTags.NET_PEER_IP, peerAddress.getAddress().getHostAddress());
      } else {
        span.setAttribute(MoreTags.NET_PEER_NAME, peerAddress.getHostName());
      }
    } else {
      // The spec says these fields must be populated, so put some values in even if we don't have
      // an address recorded.
      span.setAttribute(MoreTags.NET_PEER_PORT, 0);
      span.setAttribute(MoreTags.NET_PEER_NAME, "(unknown)");
    }
  }
}

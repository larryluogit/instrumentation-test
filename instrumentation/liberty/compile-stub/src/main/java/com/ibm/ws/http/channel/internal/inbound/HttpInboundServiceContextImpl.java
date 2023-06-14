/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.ws.http.channel.internal.inbound;

import com.ibm.wsspi.http.channel.HttpRequestMessage;
import java.net.InetAddress;

// https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/ws/http/channel/internal/inbound/HttpInboundServiceContextImpl.java
@SuppressWarnings("OtelInternalJavadoc")
public class HttpInboundServiceContextImpl {

  public HttpRequestMessage getRequest() {
    throw new UnsupportedOperationException();
  }

  public InetAddress getLocalAddr() {
    throw new UnsupportedOperationException();
  }

  public int getLocalPort() {
    throw new UnsupportedOperationException();
  }

  public InetAddress getRemoteAddr() {
    throw new UnsupportedOperationException();
  }

  public int getRemotePort() {
    throw new UnsupportedOperationException();
  }
}

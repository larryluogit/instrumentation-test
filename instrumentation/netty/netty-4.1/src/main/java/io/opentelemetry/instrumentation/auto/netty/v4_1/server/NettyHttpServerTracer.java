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

package io.opentelemetry.instrumentation.auto.netty.v4_1.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;

import io.grpc.Context;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.instrumentation.auto.netty.v4_1.AttributeKeys;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class NettyHttpServerTracer
    extends HttpServerTracer<HttpRequest, HttpResponse, Channel, Channel> {
  public static final NettyHttpServerTracer TRACER = new NettyHttpServerTracer();

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(HttpResponse httpResponse) {
    return httpResponse.status().code();
  }

  @Override
  protected void attachServerContext(Context context, Channel channel) {
    channel.attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).set(context);
  }

  @Override
  public Context getServerContext(Channel channel) {
    return channel.attr(AttributeKeys.SERVER_ATTRIBUTE_KEY).get();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.netty-4.1";
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return NettyRequestExtractAdapter.GETTER;
  }

  @Override
  protected String url(HttpRequest request) {
    String uri = request.uri();
    if (isRelativeUrl(uri) && request.headers().contains(HOST)) {
      return "http://" + request.headers().get(HOST) + request.uri();
    } else {
      return uri;
    }
  }

  @Override
  protected String peerHostIP(Channel channel) {
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected String flavor(Channel channel, HttpRequest request) {
    return request.protocolVersion().toString();
  }

  @Override
  protected Integer peerPort(Channel channel) {
    SocketAddress socketAddress = channel.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) socketAddress).getPort();
    }
    return null;
  }
}

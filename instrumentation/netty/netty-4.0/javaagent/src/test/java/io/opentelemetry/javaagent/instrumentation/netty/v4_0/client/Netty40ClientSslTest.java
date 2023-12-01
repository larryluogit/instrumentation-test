/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.client;

import static io.opentelemetry.api.trace.SpanKind.*;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ClientSslTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private HttpClientTestServer server;
  private EventLoopGroup eventLoopGroup;

  @BeforeEach
  public void setupSpec() {
    server = new HttpClientTestServer(testing.getOpenTelemetry());
    server.start();
    eventLoopGroup = new NioEventLoopGroup();
  }

  @AfterEach
  public void cleanupSpec() throws Exception {
    eventLoopGroup.shutdownGracefully();
    server.stop().get(10, TimeUnit.SECONDS);
  }

  @Test
  public void shouldFailSSLHandshake() {
    Bootstrap bootstrap = createBootstrap(eventLoopGroup, Collections.singletonList("SSLv3"));

    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());

    Throwable thrownException = getThrowable(bootstrap, uri, request);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasStatus(StatusData.error())
                        .hasException(thrownException),
                span -> {
                  span.hasName("CONNECT").hasKind(INTERNAL).hasParent(trace.getSpan(0));
                  span.hasAttributesSatisfyingExactly(
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(SemanticAttributes.SERVER_ADDRESS, uri.getHost()),
                      equalTo(SemanticAttributes.SERVER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> {
                  span.hasName("SSL handshake")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.error());
                  span.hasAttributesSatisfyingExactly(
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
                }));
  }

  private static Throwable getThrowable(
      Bootstrap bootstrap, URI uri, DefaultFullHttpRequest request) {
    AtomicReference<Channel> channel = new AtomicReference<>();
    Throwable thrown =
        catchThrowable(
            () ->
                testing.runWithSpan(
                    "parent",
                    () -> {
                      channel.set(bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel());
                      CompletableFuture<Integer> result = new CompletableFuture<>();
                      channel.get().pipeline().addLast(new ClientHandler(result));
                      channel.get().writeAndFlush(request).get(10, TimeUnit.SECONDS);
                      result.get(10, TimeUnit.SECONDS);
                    }));

    // Then
    Throwable thrownException;
    if (thrown instanceof ExecutionException) {
      thrownException = thrown.getCause();
    } else {
      thrownException = thrown;
    }
    return thrownException;
  }

  @Test
  public void shouldSuccessfullyEstablishSSLHandshake() {
    // given
    Bootstrap bootstrap =
        createBootstrap(eventLoopGroup, Arrays.asList("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"));

    URI uri = server.resolveHttpsAddress("/success");
    DefaultFullHttpRequest request =
        new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getPath(), Unpooled.EMPTY_BUFFER);
    HttpHeaders.setHost(request, uri.getHost() + ":" + uri.getPort());

    AtomicReference<Channel> channel = new AtomicReference<>();
    // when
    testing.runWithSpan(
        "parent",
        () -> {
          try {
            channel.set(bootstrap.connect(uri.getHost(), uri.getPort()).sync().channel());
            CompletableFuture<Integer> result = new CompletableFuture<>();
            channel.get().pipeline().addLast(new ClientHandler(result));
            channel.get().writeAndFlush(request).get(10, TimeUnit.SECONDS);
            result.get(10, TimeUnit.SECONDS);
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            if (channel.get() != null) {
              channel.get().close();
            }
          }
        });

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasStatus(StatusData.ok()),
                span -> {
                  span.hasName("CONNECT").hasKind(INTERNAL).hasParent(trace.getSpan(0));
                  span.hasAttributesSatisfyingExactly(
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(SemanticAttributes.SERVER_ADDRESS, uri.getHost()),
                      equalTo(SemanticAttributes.SERVER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> {
                  span.hasName("SSL handshake")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.error());
                  span.hasAttributesSatisfyingExactly(
                      equalTo(SemanticAttributes.NETWORK_TRANSPORT, IP_TCP),
                      equalTo(SemanticAttributes.NETWORK_TYPE, "ipv4"),
                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, uri.getPort()),
                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, "127.0.0.1"));
                },
                span -> {
                  span.hasName("GET")
                      .hasKind(CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasStatus(StatusData.ok());
                },
                span -> {
                  span.hasName("test-http-server")
                      .hasKind(SERVER)
                      .hasParent(trace.getSpan(3))
                      .hasStatus(StatusData.ok());
                }));
    // cleanup
    if (channel.get() != null) {
      channel.get().close();
    }
  }

  // list of default ciphers copied from netty's JdkSslContext
  private static final String[] SUPPORTED_CIPHERS =
      new String[] {
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
      };

  private static Bootstrap createBootstrap(
      EventLoopGroup eventLoopGroup, List<String> enabledProtocols) {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap
        .group(eventLoopGroup)
        .channel(NioSocketChannel.class)
        .handler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(@NotNull SocketChannel socketChannel) throws Exception {
                ChannelPipeline pipeline = socketChannel.pipeline();

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, null, null);
                javax.net.ssl.SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(true);
                sslEngine.setEnabledProtocols(enabledProtocols.toArray(new String[0]));
                sslEngine.setEnabledCipherSuites(SUPPORTED_CIPHERS);
                pipeline.addLast(new SslHandler(sslEngine));

                pipeline.addLast(new HttpClientCodec());
              }
            });
    return bootstrap;
  }
}

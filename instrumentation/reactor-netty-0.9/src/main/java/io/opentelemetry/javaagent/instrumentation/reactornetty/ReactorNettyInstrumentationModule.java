/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.netty.bootstrap.Bootstrap;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;

/**
 * This instrumentation solves the problem of the correct context propagation through the roller
 * coaster of Project Reactor and Netty thread hopping. It uses two public hooks of {@link
 * HttpClient}: {@link HttpClient#mapConnect(BiFunction)} and {@link
 * HttpClient#doOnRequest(BiConsumer)} two short-cut context propagation from the caller to Reactor
 * to Netty.
 */
@AutoService(InstrumentationModule.class)
public final class ReactorNettyInstrumentationModule extends InstrumentationModule {

  public ReactorNettyInstrumentationModule() {
    super("reactor-netty", "reactor-netty-0.9");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      ReactorNettyInstrumentationModule.class.getName() + "$MapConnect",
      ReactorNettyInstrumentationModule.class.getName() + "$OnRequest",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.AttributeKeys$1",
      // these below a transitive dependencies of AttributeKeys from above
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyHttpClientTracer",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyResponseInjectAdapter",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientRequestTracingHandler",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientResponseTracingHandler",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.NettyHttpServerTracer",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.NettyRequestExtractAdapter",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerRequestTracingHandler",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerResponseTracingHandler",
      "io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }

  private static final class HttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("reactor.netty.http.client.HttpClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isStatic().and(named("create")),
          ReactorNettyInstrumentationModule.class.getName() + "$CreateAdvice");
    }
  }

  public static class CreateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Return(readOnly = false) HttpClient client) {

      if (CallDepthThreadLocalMap.decrementCallDepth(HttpClient.class) == 0 && throwable == null) {
        client = client.doOnRequest(new OnRequest()).mapConnect(new MapConnect());
      }
    }
  }

  public static class MapConnect
      implements BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {
    @Override
    public Mono<? extends Connection> apply(Mono<? extends Connection> m, Bootstrap b) {
      return m.subscriberContext(s -> s.put("otel_context", Context.current()));
    }
  }

  public static class OnRequest implements BiConsumer<HttpClientRequest, Connection> {
    @Override
    public void accept(HttpClientRequest r, Connection c) {
      Context context = r.currentContext().get("otel_context");
      c.channel().attr(AttributeKeys.PARENT_CONNECT_CONTEXT_ATTRIBUTE_KEY).set(context);
    }
  }
}

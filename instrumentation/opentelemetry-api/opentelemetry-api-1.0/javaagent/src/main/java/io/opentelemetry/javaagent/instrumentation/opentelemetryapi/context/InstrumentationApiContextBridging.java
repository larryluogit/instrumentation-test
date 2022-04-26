/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context;

import application.io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

final class InstrumentationApiContextBridging {

  static List<ContextKeyBridge<?, ?>> instrumentationApiBridges() {
    List<ContextKeyBridge<?, ?>> bridges = new ArrayList<>();

    try {
      bridges.add(
          new ContextKeyBridge<Span, io.opentelemetry.api.trace.Span>(
              "application.io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              "io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              Bridging::toApplication,
              Bridging::toAgentOrNull));
    } catch (Throwable e) {
      // no instrumentation-api on classpath
    }

    try {
      // old SERVER_KEY bridge - needed to make legacy ServerSpan work, for users who're using old
      // instrumentation-api version with the newest agent version
      bridges.add(
          new ContextKeyBridge<Span, io.opentelemetry.api.trace.Span>(
              "application.io.opentelemetry.instrumentation.api.internal.SpanKey",
              "io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan",
              "SERVER_KEY",
              "KEY",
              Bridging::toApplication,
              Bridging::toAgentOrNull));
    } catch (Throwable e) {
      // no old instrumentation-api on classpath
    }

    ContextKeyBridge<?, ?> httpRouteHolderBridge = httpRouteStateBridge();
    if (httpRouteHolderBridge != null) {
      bridges.add(httpRouteHolderBridge);
    }

    return bridges;
  }

  private static final Class<?> AGENT_HTTP_ROUTE_STATE;
  private static final MethodHandle AGENT_CREATE;
  private static final MethodHandle AGENT_GET_UPDATED_BY_SOURCE_ORDER;
  private static final MethodHandle AGENT_GET_ROUTE;

  private static final Class<?> APPLICATION_HTTP_ROUTE_STATE;
  private static final MethodHandle APPLICATION_CREATE;
  private static final MethodHandle APPLICATION_GET_UPDATED_BY_SOURCE_ORDER;
  private static final MethodHandle APPLICATION_GET_ROUTE;

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    Class<?> agentHttpRouteState = null;
    MethodHandle agentCreate = null;
    MethodHandle agentGetUpdatedBySourceOrder = null;
    MethodHandle agentGetRoute = null;
    Class<?> applicationHttpRouteState = null;
    MethodHandle applicationCreate = null;
    MethodHandle applicationGetUpdatedBySourceOrder = null;
    MethodHandle applicationGetRoute = null;

    try {
      agentHttpRouteState =
          Class.forName("io.opentelemetry.instrumentation.api.internal.HttpRouteState");
      agentCreate =
          lookup.findStatic(
              agentHttpRouteState,
              "create",
              MethodType.methodType(agentHttpRouteState, int.class, String.class));
      agentGetUpdatedBySourceOrder =
          lookup.findVirtual(
              agentHttpRouteState, "getUpdatedBySourceOrder", MethodType.methodType(int.class));
      agentGetRoute =
          lookup.findVirtual(agentHttpRouteState, "getRoute", MethodType.methodType(String.class));

      applicationHttpRouteState =
          Class.forName("application.io.opentelemetry.instrumentation.api.internal.HttpRouteState");
      applicationCreate =
          lookup.findStatic(
              applicationHttpRouteState,
              "create",
              MethodType.methodType(applicationHttpRouteState, int.class, String.class));
      applicationGetUpdatedBySourceOrder =
          lookup.findVirtual(
              applicationHttpRouteState,
              "getUpdatedBySourceOrder",
              MethodType.methodType(int.class));
      applicationGetRoute =
          lookup.findVirtual(
              applicationHttpRouteState, "getRoute", MethodType.methodType(String.class));
    } catch (Throwable ignored) {
      // instrumentation-api may be absent on the classpath, or it might be an older version
    }

    AGENT_HTTP_ROUTE_STATE = agentHttpRouteState;
    AGENT_CREATE = agentCreate;
    AGENT_GET_UPDATED_BY_SOURCE_ORDER = agentGetUpdatedBySourceOrder;
    AGENT_GET_ROUTE = agentGetRoute;
    APPLICATION_HTTP_ROUTE_STATE = applicationHttpRouteState;
    APPLICATION_CREATE = applicationCreate;
    APPLICATION_GET_UPDATED_BY_SOURCE_ORDER = applicationGetUpdatedBySourceOrder;
    APPLICATION_GET_ROUTE = applicationGetRoute;
  }

  @Nullable
  private static ContextKeyBridge<?, ?> httpRouteStateBridge() {
    if (APPLICATION_HTTP_ROUTE_STATE == null
        || APPLICATION_CREATE == null
        || APPLICATION_GET_UPDATED_BY_SOURCE_ORDER == null
        || APPLICATION_GET_ROUTE == null) {
      // HttpRouteHolder not on application classpath; or an old version of it
      return null;
    }
    try {
      return new ContextKeyBridge<>(
          APPLICATION_HTTP_ROUTE_STATE,
          AGENT_HTTP_ROUTE_STATE,
          "KEY",
          "KEY",
          httpRouteStateConvert(
              APPLICATION_CREATE, AGENT_GET_UPDATED_BY_SOURCE_ORDER, AGENT_GET_ROUTE),
          httpRouteStateConvert(
              AGENT_CREATE, APPLICATION_GET_UPDATED_BY_SOURCE_ORDER, APPLICATION_GET_ROUTE));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Function<Object, Object> httpRouteStateConvert(
      MethodHandle create, MethodHandle getUpdatedBySourceOrder, MethodHandle getRoute) {
    return httpRouteHolder -> {
      try {
        int updatedBySourceOrder = (int) getUpdatedBySourceOrder.invoke(httpRouteHolder);
        String route = (String) getRoute.invoke(httpRouteHolder);
        return create.invoke(updatedBySourceOrder, route);
      } catch (Throwable e) {
        return null;
      }
    };
  }

  private InstrumentationApiContextBridging() {}
}

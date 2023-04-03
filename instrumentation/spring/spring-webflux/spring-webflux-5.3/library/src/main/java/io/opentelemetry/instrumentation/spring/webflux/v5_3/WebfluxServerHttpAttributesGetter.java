/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;

enum WebfluxServerHttpAttributesGetter
    implements HttpServerAttributesGetter<ServerWebExchange, ServerWebExchange> {
  INSTANCE;

  @Override
  public String getMethod(ServerWebExchange request) {
    return request.getRequest().getMethodValue();
  }

  @Override
  public List<String> getRequestHeader(ServerWebExchange request, String name) {
    return request.getRequest().getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public Integer getStatusCode(
      ServerWebExchange request, ServerWebExchange response, @Nullable Throwable error) {
    return response.getResponse().getRawStatusCode();
  }

  @Override
  public List<String> getResponseHeader(
      ServerWebExchange request, ServerWebExchange response, String name) {
    return response.getResponse().getHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public String getFlavor(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public String getTarget(ServerWebExchange request) {
    String path = request.getRequest().getURI().getPath();
    String query = request.getRequest().getURI().getQuery();
    if (path == null && query == null) {
      return null;
    }
    return (path == null ? "" : path) + (query == null ? "" : "?" + query);
  }

  @Nullable
  @Override
  public String getRoute(ServerWebExchange request) {
    Object bestPatternObj = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (bestPatternObj == null) {
      return null;
    }
    String route;
    if (bestPatternObj instanceof PathPattern) {
      route = ((PathPattern) bestPatternObj).getPatternString();
    } else {
      route = bestPatternObj.toString();
    }
    if (route.equals("/**")) {
      return null;
    }
    String contextPath = request.getRequest().getPath().contextPath().value();
    return contextPath + (route.startsWith("/") ? route : ("/" + route));
  }

  @Nullable
  @Override
  public String getScheme(ServerWebExchange request) {
    return request.getRequest().getURI().getScheme();
  }
}

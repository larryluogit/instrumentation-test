/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class HttpSpanDecorator extends BaseSpanDecorator {

  private static final String POST_METHOD = "POST";
  private static final String GET_METHOD = "GET";
  private static final Set<String> knownMethods = CommonConfig.get().getKnownHttpRequestMethods();

  protected String getProtocol() {
    return "http";
  }

  protected static String getHttpMethod(Exchange exchange, Endpoint endpoint) {
    // 1. Use method provided in header.
    Object method = exchange.getIn().getHeader(Exchange.HTTP_METHOD);
    if (method instanceof String) {
      return (String) method;
    }

    // 2. GET if query string is provided in header.
    if (exchange.getIn().getHeader(Exchange.HTTP_QUERY) != null) {
      return GET_METHOD;
    }

    // 3. GET if endpoint is configured with a query string.
    if (endpoint.getEndpointUri().indexOf('?') != -1) {
      return GET_METHOD;
    }

    // 4. POST if there is data to send (body is not null).
    if (exchange.getIn().getBody() != null) {
      return POST_METHOD;
    }

    // 5. GET otherwise.
    return GET_METHOD;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    // Based on HTTP component documentation:
    String spanName = getHttpMethod(exchange, endpoint);
    if (shouldAppendHttpRoute(camelDirection)) {
      spanName = spanName + " " + getPath(exchange, endpoint);
    }
    return spanName;
  }

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection) {
    super.pre(attributes, exchange, endpoint, camelDirection);

    String httpUrl = getHttpUrl(exchange, endpoint);
    if (httpUrl != null) {
      if (SemconvStability.emitStableHttpSemconv()) {
        internalSet(attributes, UrlAttributes.URL_FULL, httpUrl);
      }

      if (SemconvStability.emitOldHttpSemconv()) {
        internalSet(attributes, SemanticAttributes.HTTP_URL, httpUrl);
      }
    }

    String method = getHttpMethod(exchange, endpoint);
    if (SemconvStability.emitStableHttpSemconv()) {
      if (method == null || knownMethods.contains(method)) {
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, method);
      } else {
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, _OTHER);
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method);
      }
    }
    if (SemconvStability.emitOldHttpSemconv()) {
      internalSet(attributes, SemanticAttributes.HTTP_METHOD, method);
    }
  }

  private static boolean shouldAppendHttpRoute(CamelDirection camelDirection) {
    return camelDirection == CamelDirection.INBOUND;
  }

  @Nullable
  protected String getPath(Exchange exchange, Endpoint endpoint) {

    String httpUrl = getHttpUrl(exchange, endpoint);
    try {
      URL url = new URL(httpUrl);
      return url.getPath();
    } catch (MalformedURLException e) {
      return null;
    }
  }

  @Override
  public void updateServerSpanName(
      Context context,
      Exchange camelExchange,
      Endpoint camelEndpoint,
      CamelDirection camelDirection) {
    if (!shouldAppendHttpRoute(camelDirection)) {
      return;
    }
    HttpServerRoute.update(
        context,
        HttpServerRouteSource.CONTROLLER,
        (c, exchange, endpoint) -> getPath(exchange, endpoint),
        camelExchange,
        camelEndpoint);
  }

  protected String getHttpUrl(Exchange exchange, Endpoint endpoint) {
    Object url = exchange.getIn().getHeader(Exchange.HTTP_URL);
    if (url instanceof String) {
      return (String) url;
    } else {
      Object uri = exchange.getIn().getHeader(Exchange.HTTP_URI);
      if (uri instanceof String) {
        return (String) uri;
      } else {
        // Try to obtain from endpoint
        int index = endpoint.getEndpointUri().lastIndexOf(getProtocol() + ":");
        if (index != -1) {
          return endpoint.getEndpointUri().substring(index);
        }
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  public void post(AttributesBuilder attributes, Exchange exchange, Endpoint endpoint) {
    super.post(attributes, exchange, endpoint);

    if (exchange.hasOut()) {
      Object responseCode = exchange.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE);
      if (responseCode instanceof Integer) {
        if (SemconvStability.emitStableHttpSemconv()) {
          attributes.put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, (Integer) responseCode);
        }
        if (SemconvStability.emitOldHttpSemconv()) {
          attributes.put(SemanticAttributes.HTTP_STATUS_CODE, (Integer) responseCode);
        }
      }
    }
  }
}

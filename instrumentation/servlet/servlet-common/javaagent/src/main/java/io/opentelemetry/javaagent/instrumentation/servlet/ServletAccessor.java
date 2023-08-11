/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.common.AttributeKey;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This interface is used to access methods of ServletContext, HttpServletRequest and
 * HttpServletResponse classes in shared code that is used for both jakarta.servlet and
 * javax.servlet versions of those classes. A wrapper class with extra information attached may be
 * used as well in cases where the class itself does not provide some field (such as response status
 * for Servlet API 2.2).
 *
 * @param <REQUEST> HttpServletRequest class (or a wrapper)
 * @param <RESPONSE> HttpServletResponse class (or a wrapper)
 */
public interface ServletAccessor<REQUEST, RESPONSE> {
  String getRequestContextPath(REQUEST request);

  String getRequestScheme(REQUEST request);

  String getRequestUri(REQUEST request);

  String getRequestQueryString(REQUEST request);

  Object getRequestAttribute(REQUEST request, String name);

  void setRequestAttribute(REQUEST request, String name, Object value);

  String getRequestProtocol(REQUEST request);

  String getRequestMethod(REQUEST request);

  String getRequestServerName(REQUEST request);

  Integer getRequestServerPort(REQUEST request);

  String getRequestRemoteAddr(REQUEST request);

  Integer getRequestRemotePort(REQUEST request);

  String getRequestLocalAddr(REQUEST request);

  Integer getRequestLocalPort(REQUEST request);

  String getRequestHeader(REQUEST request, String name);

  List<String> getRequestHeaderValues(REQUEST request, String name);

  Iterable<String> getRequestHeaderNames(REQUEST request);

  List<String> getRequestParameterValues(REQUEST request, String name);

  default void forAllQueryParams(
      REQUEST request, BiConsumer<AttributeKey<List<String>>, List<String>> consumer) {
    Map<String, String[]> parameterMap = getQueryParamsMap(request);
    for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
      AttributeKey<List<String>> key = QueryParameterKeyCache.get(entry.getKey());
      consumer.accept(key, Arrays.asList(entry.getValue()));
    }
  }

  Map<String, String[]> getQueryParamsMap(REQUEST request);

  String getRequestServletPath(REQUEST request);

  String getRequestPathInfo(REQUEST request);

  Principal getRequestUserPrincipal(REQUEST request);

  void addRequestAsyncListener(
      REQUEST request, ServletAsyncListener<RESPONSE> listener, Object response);

  int getResponseStatus(RESPONSE response);

  List<String> getResponseHeaderValues(RESPONSE response, String name);

  boolean isResponseCommitted(RESPONSE response);

  boolean isServletException(Throwable throwable);
}

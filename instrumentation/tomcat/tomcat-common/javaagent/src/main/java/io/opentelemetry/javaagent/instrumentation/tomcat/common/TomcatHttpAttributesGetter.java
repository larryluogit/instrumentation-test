/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.json.JSONObject;

public class TomcatHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {

  @Override
  public String method(Request request) {
    return request.method().toString();
  }

  @Override
  @Nullable
  public String target(Request request) {
    String target = request.requestURI().toString();
    String queryString = request.queryString().toString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  @Nullable
  public String scheme(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    return schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return Collections.list(request.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public Long requestContentLength(Request request, @Nullable Response response) {
    long contentLength = request.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  @Nullable
  public String flavor(Request request) {
    String flavor = request.protocol().toString();
    if (flavor != null) {
      // remove HTTP/ prefix to comply with semantic conventions
      if (flavor.startsWith("HTTP/")) {
        flavor = flavor.substring("HTTP/".length());
      }
    }
    return flavor;
  }

  @Override
  @Nullable
  public Integer statusCode(Request request, Response response) {
    return response.getStatus();
  }

  @Override
  @Nullable
  public Long responseContentLength(Request request, Response response) {
    long contentLength = response.getContentLengthLong();
    return contentLength != -1 ? contentLength : null;
  }

  @Override
  public List<String> responseHeader(Request request, Response response, String name) {
    return Collections.list(response.getMimeHeaders().values(name));
  }

  @Override
  @Nullable
  public String route(Request request) {
    return null;
  }

  @Override
  @Nullable
  public String serverName(Request request) {
    return null;
  }

  @Nullable
  @Override
  public String requestHeaders(Request request, @Nullable Response response) {
    return toJsonString(mimeHeadersToMap(request.getMimeHeaders()));
  }

  @Nullable
  @Override
  public String responseHeaders(Request request, Response response) {
    return toJsonString(mimeHeadersToMap(response.getMimeHeaders()));
  }

  private Map<String, String> mimeHeadersToMap(MimeHeaders headers) {
    return Collections.list(headers.names()).stream()
        .collect(Collectors.toMap(Function.identity(), headers::getHeader));
  }

  private static String toJsonString(Map<String, String> m) {
    return new JSONObject(m).toString();
  }
}

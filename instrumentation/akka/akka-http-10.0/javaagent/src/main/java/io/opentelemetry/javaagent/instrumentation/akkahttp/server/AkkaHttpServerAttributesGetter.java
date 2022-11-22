/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;
import scala.Option;

class AkkaHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String method(HttpRequest request) {
    return request.method().value();
  }

  @Override
  public List<String> requestHeader(HttpRequest request, String name) {
    return AkkaHttpUtil.requestHeader(request, name);
  }

  @Override
  public Integer statusCode(
      HttpRequest request, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.status().intValue();
  }

  @Override
  public List<String> responseHeader(HttpRequest request, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }

  @Override
  public String flavor(HttpRequest request) {
    return AkkaHttpUtil.flavor(request);
  }

  @Override
  public String target(HttpRequest request) {
    String target = request.uri().path().toString();
    Option<String> queryString = request.uri().rawQueryString();
    if (queryString.isDefined()) {
      target += "?" + queryString.get();
    }
    return target;
  }

  @Override
  @Nullable
  public String route(HttpRequest request) {
    return null;
  }

  @Override
  public String scheme(HttpRequest request) {
    return request.uri().scheme();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class GoogleHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  @Nullable
  public String getMethod(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.getUrl().build();
  }

  @Override
  public List<String> getRequestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getHeaderStringValues(name);
  }

  @Override
  public Integer getStatusCode(
      HttpRequest httpRequest, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.getStatusCode();
  }

  @Override
  public List<String> getResponseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getHeaderStringValues(name);
  }
}

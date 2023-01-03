/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String getMethod(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrl(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> getRequestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer getStatusCode(
      ApacheHttpClientRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.getCode();
  }

  @Override
  @Nullable
  public String getFlavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    String flavor = request.getFlavor();
    if (flavor == null && response != null) {
      flavor = ApacheHttpClientAttributesHelper.getFlavor(response.getVersion());
    }
    return flavor;
  }

  @Override
  public List<String> getResponseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return ApacheHttpClientAttributesHelper.getHeader(response, name);
  }
}

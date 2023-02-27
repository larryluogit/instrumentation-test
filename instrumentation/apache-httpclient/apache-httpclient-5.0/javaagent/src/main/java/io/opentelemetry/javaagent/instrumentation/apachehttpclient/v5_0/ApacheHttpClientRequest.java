/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.OtelHttpRequest;
import java.util.List;
import org.apache.hc.core5.http.HttpRequest;

public final class ApacheHttpClientRequest implements OtelHttpRequest {
  private final HttpRequest httpRequest;

  public ApacheHttpClientRequest(HttpRequest httpRequest) {
    this.httpRequest = httpRequest;
  }

  @Override
  public String getPeerName() {
    return httpRequest.getAuthority().getHostName();
  }

  @Override
  public Integer getPeerPort() {
    return ApacheHttpClientAttributesHelper.getPeerPort(httpRequest);
  }

  @Override
  public String getMethod() {
    return httpRequest.getMethod();
  }

  @Override
  public String getUrl() {
    return ApacheHttpClientAttributesHelper.getUrl(httpRequest);
  }

  @Override
  public String getFlavor() {
    return ApacheHttpClientAttributesHelper.getFlavor(httpRequest.getVersion());
  }

  @Override
  public List<String> getHeader(String name) {
    return ApacheHttpClientAttributesHelper.getHeader(httpRequest, name);
  }

  @Override
  public String getFirstHeader(String name) {
    return ApacheHttpClientAttributesHelper.getFirstHeader(httpRequest, name);
  }

  @Override
  public void setHeader(String key, String value) {
    httpRequest.setHeader(key, value);
  }
}

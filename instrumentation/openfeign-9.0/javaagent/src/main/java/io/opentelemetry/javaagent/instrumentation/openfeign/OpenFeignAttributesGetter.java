/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.Request;
import feign.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcAttributesGetter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum OpenFeignAttributesGetter
    implements
        RpcAttributesGetter<ExecuteAndDecodeRequest>,
        NetClientAttributesGetter<ExecuteAndDecodeRequest, feign.Response>,
        HttpClientAttributesGetter<ExecuteAndDecodeRequest, feign.Response> {
  INSTANCE;

  @Override
  public String system(ExecuteAndDecodeRequest request) {
    return "openfeign";
  }

  @Override
  public String service(ExecuteAndDecodeRequest request) {
    return request.getTarget().name();
  }

  @Override
  public String method(ExecuteAndDecodeRequest request) {
    return request.getRequestTemplate().method();
  }

  @Override
  public List<String> requestHeader(ExecuteAndDecodeRequest request, String name) {
    Request req = request.getRequestTemplate().request();
    return new ArrayList<>(req.headers().getOrDefault(name, Collections.emptyList()));
  }

  @Nullable
  @Override
  public Long requestContentLength(ExecuteAndDecodeRequest request, @Nullable Response response) {
    byte[] body = request.getRequestTemplate().request().body();
    return body != null ? (long) body.length : null;
  }

  @Nullable
  @Override
  public Long requestContentLengthUncompressed(
      ExecuteAndDecodeRequest request, @Nullable Response response) {
    return null;
  }

  @Override
  public Integer statusCode(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return response != null ? response.status() : 0;
  }

  @Override
  public Long responseContentLength(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return (response != null && response.body() != null) ? response.body().length().longValue() : 0;
  }

  @Nullable
  @Override
  public Long responseContentLengthUncompressed(
      ExecuteAndDecodeRequest request, @Nullable Response response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ExecuteAndDecodeRequest request, @Nullable Response response, String name) {
    return response != null
        ? new ArrayList<>(response.headers().getOrDefault(name, Collections.emptyList()))
        : Collections.emptyList();
  }

  @Override
  public String url(ExecuteAndDecodeRequest request) {
    return request.getTarget().url() + request.getTemplateUri().getPath();
  }

  @Nullable
  @Override
  public String flavor(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return null;
  }

  @Override
  public String transport(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return "http";
  }

  @Override
  public String peerName(ExecuteAndDecodeRequest request, @Nullable Response response) {
    String host = request.getTemplateUri().getHost();
    if (host == null || "".equals(host)) {
      return service(request);
    }
    return host;
  }

  @Override
  public Integer peerPort(ExecuteAndDecodeRequest request, @Nullable Response response) {
    URI uri = request.getTemplateUri();
    return uri.getPort();
  }

  @Nullable
  @Override
  public String peerIp(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return null;
  }
}

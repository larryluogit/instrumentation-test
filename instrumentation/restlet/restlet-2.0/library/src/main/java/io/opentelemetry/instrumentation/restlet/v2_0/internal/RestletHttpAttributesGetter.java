/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static io.opentelemetry.instrumentation.restlet.v2_0.internal.RestletHeadersGetter.getHeaders;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Reference;
import org.restlet.util.Series;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum RestletHttpAttributesGetter implements HttpServerAttributesGetter<Request, Response> {
  INSTANCE;

  @Override
  public String getMethod(Request request) {
    return request.getMethod().toString();
  }

  @Override
  @Nullable
  public String getTarget(Request request) {
    Reference ref = request.getOriginalRef();
    String path = ref.getPath();
    return ref.hasQuery() ? path + "?" + ref.getQuery() : path;
  }

  @Override
  @Nullable
  public String getScheme(Request request) {
    return request.getOriginalRef().getScheme();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    Series<?> headers = getHeaders(request);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
  }

  @Override
  @Nullable
  public String getFlavor(Request request) {
    switch (request.getProtocol().toString()) {
      case "HTTP/1.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
      case "HTTP/1.1":
        return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
      case "HTTP/2.0":
        return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
      default:
        // fall through
    }
    return null;
  }

  @Override
  public Integer getStatusCode(Request request, Response response, @Nullable Throwable error) {
    return response.getStatus().getCode();
  }

  @Override
  public List<String> getResponseHeader(Request request, Response response, String name) {
    Series<?> headers = getHeaders(response);
    if (headers == null) {
      return Collections.emptyList();
    }
    return Arrays.asList(headers.getValuesArray(name, true));
  }
}

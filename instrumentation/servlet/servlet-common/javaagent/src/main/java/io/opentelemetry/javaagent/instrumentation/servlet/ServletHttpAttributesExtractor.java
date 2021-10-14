/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServletHttpAttributesExtractor<REQUEST, RESPONSE>
    extends HttpServerAttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  protected final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletHttpAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  protected @Nullable String method(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestMethod(requestContext.request());
  }

  @Override
  protected @Nullable String target(ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    String target = accessor.getRequestUri(request);
    String queryString = accessor.getRequestQueryString(request);
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  protected @Nullable String scheme(ServletRequestContext<REQUEST> requestContext) {
    return accessor.getRequestScheme(requestContext.request());
  }

  @Override
  protected List<String> requestHeader(ServletRequestContext<REQUEST> requestContext, String name) {
    return accessor.getRequestHeaderValues(requestContext.request(), name);
  }

  @Override
  protected @Nullable Long requestContentLength(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    int contentLength = accessor.getRequestContentLength(requestContext.request());
    if (contentLength > -1) {
      return (long) contentLength;
    }
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  protected @Nullable String flavor(ServletRequestContext<REQUEST> requestContext) {
    String flavor = accessor.getRequestProtocol(requestContext.request());
    if (flavor != null) {
      // remove HTTP/ prefix to comply with semantic conventions
      if (flavor.startsWith("HTTP/")) {
        flavor = flavor.substring("HTTP/".length());
      }
    }
    return flavor;
  }

  @Override
  protected @Nullable Integer statusCode(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    RESPONSE response = responseContext.response();

    if (!accessor.isResponseCommitted(response) && responseContext.error() != null) {
      // if response is not committed and there is a throwable set status to 500 /
      // INTERNAL_SERVER_ERROR, due to servlet spec
      // https://javaee.github.io/servlet-spec/downloads/servlet-4.0/servlet-4_0_FINAL.pdf:
      // "If a servlet generates an error that is not handled by the error page mechanism as
      // described above, the container must ensure to send a response with status 500."
      return 500;
    }
    return accessor.getResponseStatus(response);
  }

  @Override
  protected @Nullable Long responseContentLength(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    String contentLength = accessor.getResponseHeader(responseContext.response(), "Content-Length");
    if (contentLength != null) {
      try {
        return Long.valueOf(contentLength);
      } catch (NumberFormatException ignored) {
        // ignore
      }
    }
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ServletRequestContext<REQUEST> requestContext,
      ServletResponseContext<RESPONSE> responseContext,
      String name) {
    return accessor.getResponseHeaderValues(responseContext.response(), name);
  }

  @Override
  protected @Nullable String route(ServletRequestContext<REQUEST> requestContext) {
    return null;
  }

  @Override
  protected @Nullable String serverName(
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext) {
    return accessor.getRequestServerName(requestContext.request());
  }
}

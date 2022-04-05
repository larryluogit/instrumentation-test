/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.emptyIfNull;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.lowercaseMap;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.FAAS_TRIGGER;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_METHOD;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_STATUS_CODE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_URL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.HTTP_USER_AGENT;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.annotation.Nullable;

final class ApiGatewayProxyAttributesExtractor
    implements AttributesExtractor<AwsLambdaRequest, Object> {
  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, AwsLambdaRequest request) {
    if (request.getInput() instanceof APIGatewayProxyRequestEvent) {
      attributes.put(FAAS_TRIGGER, SemanticAttributes.FaasTriggerValues.HTTP);
      onRequest(attributes, (APIGatewayProxyRequestEvent) request.getInput());
    }
  }

  void onRequest(AttributesBuilder attributes, APIGatewayProxyRequestEvent request) {
    attributes.put(HTTP_METHOD, request.getHttpMethod());

    Map<String, String> headers = lowercaseMap(request.getHeaders());
    String userAgent = headers.get("user-agent");
    if (userAgent != null) {
      attributes.put(HTTP_USER_AGENT, userAgent);
    }
    String httpUrl = getHttpUrl(request, headers);
    if (httpUrl != null) {
      attributes.put(HTTP_URL, httpUrl);
    }
  }

  private static String getHttpUrl(
      APIGatewayProxyRequestEvent request, Map<String, String> headers) {
    StringBuilder str = new StringBuilder();

    String scheme = headers.get("x-forwarded-proto");
    if (scheme != null) {
      str.append(scheme).append("://");
    }
    String host = headers.get("host");
    if (host != null) {
      str.append(host);
    }
    String path = request.getPath();
    if (path != null) {
      str.append(path);
    }

    try {
      boolean first = true;
      for (Map.Entry<String, String> entry :
          emptyIfNull(request.getQueryStringParameters()).entrySet()) {
        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
        String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name());
        str.append(first ? '?' : '&').append(key).append('=').append(value);
        first = false;
      }
    } catch (UnsupportedEncodingException ignored) {
      // Ignore
    }
    return str.length() == 0 ? null : str.toString();
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (response instanceof APIGatewayProxyResponseEvent) {
      Integer statusCode = ((APIGatewayProxyResponseEvent) response).getStatusCode();
      if (statusCode != null) {
        attributes.put(HTTP_STATUS_CODE, statusCode);
      }
    }
  }

  ApiGatewayProxyAttributesExtractor() {}
}

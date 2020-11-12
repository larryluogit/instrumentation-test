/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static io.opentelemetry.instrumentation.awslambda.v1_0.HeadersFactory.ofStream;

import com.amazonaws.serverless.proxy.model.Headers;
import io.opentelemetry.api.OpenTelemetry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

abstract class ApiGatewayProxyRequest {

  private static boolean noHttpPropagationNeeded() {
    List<String> fields = OpenTelemetry.getGlobalPropagators().getTextMapPropagator().fields();
    return (fields.isEmpty() || xRayPropagationFieldsOnly(fields));
  }

  private static boolean xRayPropagationFieldsOnly(List<String> fields) {
    // ugly but faster than typical convert-to-set-and-check-contains-only
    return (fields.size() == 1)
        && (ParentContextExtractor.AWS_TRACE_HEADER_PROPAGATOR_KEY.equals(fields.get(0)));
  }

  static ApiGatewayProxyRequest forStream(final InputStream source) throws IOException {

    if (noHttpPropagationNeeded()) {
      return new NoopRequest(source);
    }

    if (source.markSupported()) {
      return new MarkableApiGatewayProxyRequest(source);
    }
    // fallback
    return new CopiedApiGatewayProxyRequest(source);
  }

  @Nullable
  Headers getHeaders() throws IOException {
    return ofStream(freshStream());
  }

  abstract InputStream freshStream() throws IOException;

  private static class NoopRequest extends ApiGatewayProxyRequest {

    private final InputStream stream;

    private NoopRequest(InputStream stream) {
      this.stream = stream;
    }

    @Override
    InputStream freshStream() {
      return stream;
    }

    @Override
    Headers getHeaders() {
      return null;
    }
  }

  private static class MarkableApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final InputStream inputStream;

    private MarkableApiGatewayProxyRequest(InputStream inputStream) {
      this.inputStream = inputStream;
      inputStream.mark(Integer.MAX_VALUE);
    }

    @Override
    InputStream freshStream() throws IOException {

      inputStream.reset();
      inputStream.mark(Integer.MAX_VALUE);
      return inputStream;
    }
  }

  private static class CopiedApiGatewayProxyRequest extends ApiGatewayProxyRequest {

    private final byte[] data;

    private CopiedApiGatewayProxyRequest(InputStream inputStream) throws IOException {
      data = IOUtils.toByteArray(inputStream);
    }

    @Override
    InputStream freshStream() {
      return new ByteArrayInputStream(data);
    }
  }
}

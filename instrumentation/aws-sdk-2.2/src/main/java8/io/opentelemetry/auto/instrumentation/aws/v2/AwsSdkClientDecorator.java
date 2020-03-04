/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.aws.v2;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.HttpClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public class AwsSdkClientDecorator extends HttpClientDecorator<SdkHttpRequest, SdkHttpResponse> {
  public static final AwsSdkClientDecorator DECORATE = new AwsSdkClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.aws-sdk-2.2");

  static final String COMPONENT_NAME = "java-aws-sdk";

  public Span onSdkRequest(final Span span, final SdkRequest request) {
    // S3
    request
        .getValueForField("Bucket", String.class)
        .ifPresent(name -> span.setAttribute("aws.bucket.name", name));
    // SQS
    request
        .getValueForField("QueueUrl", String.class)
        .ifPresent(name -> span.setAttribute("aws.queue.url", name));
    request
        .getValueForField("QueueName", String.class)
        .ifPresent(name -> span.setAttribute("aws.queue.name", name));
    // Kinesis
    request
        .getValueForField("StreamName", String.class)
        .ifPresent(name -> span.setAttribute("aws.stream.name", name));
    // DynamoDB
    request
        .getValueForField("TableName", String.class)
        .ifPresent(name -> span.setAttribute("aws.table.name", name));
    return span;
  }

  public Span onAttributes(final Span span, final ExecutionAttributes attributes) {

    final String awsServiceName = attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME);
    final String awsOperation = attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME);

    // Resource Name has to be set after the HTTP_URL because otherwise decorators overwrite it
    span.setAttribute(MoreTags.RESOURCE_NAME, awsServiceName + "." + awsOperation);

    span.setAttribute("aws.agent", COMPONENT_NAME);
    span.setAttribute("aws.service", awsServiceName);
    span.setAttribute("aws.operation", awsOperation);

    return span;
  }

  // Not overriding the super.  Should call both with each type of response.
  public Span onResponse(final Span span, final SdkResponse response) {
    if (response instanceof AwsResponse) {
      span.setAttribute("aws.requestId", ((AwsResponse) response).responseMetadata().requestId());
    }
    return span;
  }

  @Override
  protected String service() {
    return COMPONENT_NAME;
  }

  @Override
  protected String getComponentName() {
    return COMPONENT_NAME;
  }

  @Override
  protected String method(final SdkHttpRequest request) {
    return request.method().name();
  }

  @Override
  protected URI url(final SdkHttpRequest request) {
    return request.getUri();
  }

  @Override
  protected String hostname(final SdkHttpRequest request) {
    return request.host();
  }

  @Override
  protected Integer port(final SdkHttpRequest request) {
    return request.port();
  }

  @Override
  protected Integer status(final SdkHttpResponse response) {
    return response.statusCode();
  }
}

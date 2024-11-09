/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.DeleteStreamRequest;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractAws1KinesisClientTest extends AbstractAws1BaseClientTest {

  public abstract AmazonKinesisClientBuilder configureClient(AmazonKinesisClientBuilder client);

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation,
      String method,
      Function<AmazonKinesis, Object> call,
      Map<String, String> additionalAttributes)
      throws Exception {

    AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard();

    AmazonKinesis client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    requestWithMockedResponse(response, client, "Kinesis", operation, method, additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "DeleteStream",
            "POST",
            (Function<AmazonKinesis, Object>)
                c -> c.deleteStream(new DeleteStreamRequest().withStreamName("somestream")),
            ImmutableMap.of("aws.stream.name", "somestream")),
        // Some users may implicitly subclass the request object to mimic a fluent style
        Arguments.of(
            "DeleteStream",
            "POST",
            (Function<AmazonKinesis, Object>)
                c ->
                    c.deleteStream(
                        new DeleteStreamRequest() {
                          {
                            withStreamName("somestream");
                          }
                        }),
            ImmutableMap.of("aws.stream.name", "somestream")));
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.v20_0;

import graphql.ExecutionResult;
import graphql.schema.DataFetchingEnvironment;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes;
import java.util.Locale;
import javax.annotation.Nullable;

final class GraphqlInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-20.0";

  public static Instrumenter<OpenTelemetryInstrumentationState, ExecutionResult>
      createExecutionInstrumenter(OpenTelemetry openTelemetry) {
    return Instrumenter.<OpenTelemetryInstrumentationState, ExecutionResult>builder(
            openTelemetry, INSTRUMENTATION_NAME, ignored -> "GraphQL Operation")
        .addAttributesExtractor(new GraphqlExecutionAttributesExtractor())
        .setSpanStatusExtractor(new GraphqlExecutionSpanStatusExtractor())
        .buildInstrumenter();
  }

  public static Instrumenter<DataFetchingEnvironment, Void> createDataFetcherInstrumenter(
      OpenTelemetry openTelemetry, boolean enabled) {
    return Instrumenter.<DataFetchingEnvironment, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            environment -> environment.getExecutionStepInfo().getField().getName())
        .addAttributesExtractor(new GraphqlDataFetcherAttributesExtractor())
        .setSpanStatusExtractor(
            (spanStatusBuilder, environment, unused, error) ->
                SpanStatusExtractor.getDefault()
                    .extract(spanStatusBuilder, environment, null, error))
        .setEnabled(enabled)
        .buildInstrumenter();
  }

  private GraphqlInstrumenterFactory() {}

  private static final class GraphqlExecutionAttributesExtractor
      implements AttributesExtractor<OpenTelemetryInstrumentationState, ExecutionResult> {

    @Override
    public void onStart(
        AttributesBuilder attributes,
        Context parentContext,
        OpenTelemetryInstrumentationState openTelemetryInstrumentationState) {}

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        OpenTelemetryInstrumentationState openTelemetryInstrumentationState,
        @Nullable ExecutionResult executionResult,
        @Nullable Throwable error) {
      attributes.put(
          GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME,
          openTelemetryInstrumentationState.getOperationName());
      if (openTelemetryInstrumentationState.getOperation() != null) {
        attributes.put(
            GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE,
            openTelemetryInstrumentationState.getOperation().name().toLowerCase(Locale.ROOT));
      }
      attributes.put(
          GraphqlIncubatingAttributes.GRAPHQL_DOCUMENT,
          openTelemetryInstrumentationState.getQuery());
    }
  }

  private static final class GraphqlExecutionSpanStatusExtractor
      implements SpanStatusExtractor<OpenTelemetryInstrumentationState, ExecutionResult> {
    @Override
    public void extract(
        SpanStatusBuilder spanStatusBuilder,
        OpenTelemetryInstrumentationState openTelemetryInstrumentationState,
        @Nullable ExecutionResult executionResult,
        @Nullable Throwable error) {
      if (executionResult != null && !executionResult.getErrors().isEmpty()) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      } else {
        SpanStatusExtractor.getDefault()
            .extract(spanStatusBuilder, openTelemetryInstrumentationState, executionResult, error);
      }
    }
  }

  private static final class GraphqlDataFetcherAttributesExtractor
      implements AttributesExtractor<DataFetchingEnvironment, Void> {

    // NOTE: These are not part of the Semantic Convention and are subject to change
    private static final AttributeKey<String> GRAPHQL_FIELD_NAME =
        AttributeKey.stringKey("graphql.field.name");
    private static final AttributeKey<String> GRAPHQL_FIELD_PATH =
        AttributeKey.stringKey("graphql.field.path");

    @Override
    public void onStart(
        AttributesBuilder attributes, Context parentContext, DataFetchingEnvironment environment) {
      attributes
          .put(GRAPHQL_FIELD_NAME, environment.getExecutionStepInfo().getField().getName())
          .put(GRAPHQL_FIELD_PATH, environment.getExecutionStepInfo().getPath().toString());
    }

    @Override
    public void onEnd(
        AttributesBuilder attributes,
        Context context,
        DataFetchingEnvironment environment,
        @Nullable Void unused,
        @Nullable Throwable error) {}
  }
}

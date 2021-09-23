/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.client.Response;

final class ElasticsearchRestAttributesExtractor extends DbAttributesExtractor<String, Response> {
  @Override
  protected String system(String s) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  protected @Nullable String user(String s) {
    return null;
  }

  @Override
  protected @Nullable String name(String s) {
    return null;
  }

  @Override
  protected @Nullable String connectionString(String s) {
    return null;
  }

  @Override
  protected @Nullable String statement(String s) {
    return null;
  }

  @Override
  protected @Nullable String operation(String operation) {
    return operation;
  }
}

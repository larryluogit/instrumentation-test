/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceResolver;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class CommonConfig {

  private static final CommonConfig instance = new CommonConfig(InstrumentationConfig.get());

  public static CommonConfig get() {
    return instance;
  }

  private final PeerServiceResolver peerServiceResolver;

  private final List<String> clientRequestHeaders;

  private final List<String> clientResponseHeaders;

  private final List<String> serverRequestHeaders;

  private final List<String> serverResponseHeaders;

  private final Set<String> knownHttpRequestMethods;

  private final EnduserConfig enduserConfig;

  private final boolean statementSanitizationEnabled;

  private final boolean emitExperimentalHttpClientTelemetry;

  private final boolean emitExperimentalHttpServerTelemetry;

  private final String loggingKeysTraceId;

  private final String loggingKeysSpanId;

  private final String loggingKeysTraceFlags;

  CommonConfig(InstrumentationConfig config) {
    peerServiceResolver =
        PeerServiceResolver.create(
            config.getMap("otel.instrumentation.common.peer-service-mapping", emptyMap()));

    // TODO (mateusz): remove the old config names in 2.0
    clientRequestHeaders =
        DeprecatedConfigProperties.getList(
            config,
            "otel.instrumentation.http.capture-headers.client.request",
            "otel.instrumentation.http.client.capture-request-headers");
    clientResponseHeaders =
        DeprecatedConfigProperties.getList(
            config,
            "otel.instrumentation.http.capture-headers.client.response",
            "otel.instrumentation.http.client.capture-response-headers");
    serverRequestHeaders =
        DeprecatedConfigProperties.getList(
            config,
            "otel.instrumentation.http.capture-headers.server.request",
            "otel.instrumentation.http.server.capture-request-headers");
    serverResponseHeaders =
        DeprecatedConfigProperties.getList(
            config,
            "otel.instrumentation.http.capture-headers.server.response",
            "otel.instrumentation.http.server.capture-response-headers");
    knownHttpRequestMethods =
        new HashSet<>(
            config.getList(
                "otel.instrumentation.http.known-methods",
                new ArrayList<>(HttpConstants.KNOWN_METHODS)));
    statementSanitizationEnabled =
        config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true);
    emitExperimentalHttpClientTelemetry =
        DeprecatedConfigProperties.getBoolean(
            config,
            "otel.instrumentation.http.client.emit-experimental-metrics",
            "otel.instrumentation.http.client.emit-experimental-telemetry",
            false);
    emitExperimentalHttpServerTelemetry =
        DeprecatedConfigProperties.getBoolean(
            config,
            "otel.instrumentation.http.server.emit-experimental-metrics",
            "otel.instrumentation.http.server.emit-experimental-telemetry",
            false);
    loggingKeysTraceId =
        config.getString("otel.instrumentation.common.logging.keys.trace_id", "trace_id");
    loggingKeysSpanId =
        config.getString("otel.instrumentation.common.logging.keys.span_id", "span_id");
    loggingKeysTraceFlags =
        config.getString("otel.instrumentation.common.logging.keys.trace_flags", "trace_flags");
    enduserConfig = new EnduserConfig(config);
  }

  public PeerServiceResolver getPeerServiceResolver() {
    return peerServiceResolver;
  }

  public List<String> getClientRequestHeaders() {
    return clientRequestHeaders;
  }

  public List<String> getClientResponseHeaders() {
    return clientResponseHeaders;
  }

  public List<String> getServerRequestHeaders() {
    return serverRequestHeaders;
  }

  public List<String> getServerResponseHeaders() {
    return serverResponseHeaders;
  }

  public Set<String> getKnownHttpRequestMethods() {
    return knownHttpRequestMethods;
  }

  public EnduserConfig getEnduserConfig() {
    return enduserConfig;
  }

  public boolean isStatementSanitizationEnabled() {
    return statementSanitizationEnabled;
  }

  public boolean shouldEmitExperimentalHttpClientTelemetry() {
    return emitExperimentalHttpClientTelemetry;
  }

  public boolean shouldEmitExperimentalHttpServerTelemetry() {
    return emitExperimentalHttpServerTelemetry;
  }

  public String getLoggingKeysTraceId() {
    return loggingKeysTraceId;
  }

  public String getLoggingKeysSpanId() {
    return loggingKeysSpanId;
  }

  public String getLoggingKeysTraceFlags() {
    return loggingKeysTraceFlags;
  }
}

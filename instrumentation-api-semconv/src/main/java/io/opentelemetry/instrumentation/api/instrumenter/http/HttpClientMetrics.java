/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpMessageBodySizeUtil.getHttpRequestBodySize;
import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpMessageBodySizeUtil.getHttpResponseBodySize;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyClientDurationAndSizeView;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.metrics.internal.DurationHistogram;
import io.opentelemetry.instrumentation.api.metrics.internal.DurationHistogramFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * {@link OperationListener} which keeps track of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/specification/metrics/semantic_conventions/http-metrics.md#http-client">HTTP
 * client metrics</a>.
 */
public final class HttpClientMetrics implements OperationListener {

  private static final ContextKey<State> HTTP_CLIENT_REQUEST_METRICS_STATE =
      ContextKey.named("http-client-request-metrics-state");

  private static final Logger logger = Logger.getLogger(HttpClientMetrics.class.getName());

  /**
   * Returns a {@link OperationMetrics} which can be used to enable recording of {@link
   * HttpClientMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  public static OperationMetrics get() {
    return HttpClientMetrics::new;
  }

  private final DurationHistogram duration;
  private final LongHistogram requestSize;
  private final LongHistogram responseSize;

  private HttpClientMetrics(Meter meter) {
    duration =
        DurationHistogramFactory.create(
            meter, "http.client.duration", "The duration of the outbound HTTP request");
    requestSize =
        meter
            .histogramBuilder("http.client.request.size")
            .setUnit("By")
            .setDescription("The size of HTTP request messages")
            .ofLongs()
            .build();
    responseSize =
        meter
            .histogramBuilder("http.client.response.size")
            .setUnit("By")
            .setDescription("The size of HTTP response messages")
            .ofLongs()
            .build();
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    return context.with(
        HTTP_CLIENT_REQUEST_METRICS_STATE,
        new AutoValue_HttpClientMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(HTTP_CLIENT_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.log(
          FINE,
          "No state present when ending context {0}. Cannot record HTTP request metrics.",
          context);
      return;
    }

    Attributes durationAndSizeAttributes =
        applyClientDurationAndSizeView(state.startAttributes(), endAttributes);
    duration.record(
        endNanos - state.startTimeNanos(),
        TimeUnit.NANOSECONDS,
        durationAndSizeAttributes,
        context);

    Long requestBodySize = getHttpRequestBodySize(endAttributes, state.startAttributes());
    if (requestBodySize != null) {
      requestSize.record(requestBodySize, durationAndSizeAttributes, context);
    }

    Long responseBodySize = getHttpResponseBodySize(endAttributes, state.startAttributes());
    if (responseBodySize != null) {
      responseSize.record(responseBodySize, durationAndSizeAttributes, context);
    }
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}

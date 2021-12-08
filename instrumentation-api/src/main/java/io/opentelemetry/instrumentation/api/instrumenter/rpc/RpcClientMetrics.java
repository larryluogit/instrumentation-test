/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.rpc;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * guide from
 * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/rpc.md#rpc-client
 */
@UnstableApi
public class RpcClientMetrics implements RequestListener {

  private static final ContextKey<RpcClientMetrics.State> RPC_CLIENT_REQUEST_METRICS_STATE =
      ContextKey.named("rpc-client-request-metrics-state");

  private static final Logger logger = LoggerFactory.getLogger(RpcClientMetrics.class);

  private final DoubleHistogram clientDurationHistogram;

  private RpcClientMetrics(Meter meter) {
    clientDurationHistogram = meter
        .histogramBuilder("rpc.client.duration")
        .setDescription("measures duration of outbound RPC")
        .setUnit("milliseconds")
        .build();
  }

  /**
   * Returns a {@link RequestMetrics} which can be used to enable recording of {@link
   * RpcClientMetrics} on an {@link
   * io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder}.
   */
  @UnstableApi
  public static RequestMetrics get() {
    return RpcClientMetrics::new;
  }

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    return context.with(RPC_CLIENT_REQUEST_METRICS_STATE,
        new AutoValue_RpcClientMetrics_State(startAttributes, startNanos));
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(RPC_CLIENT_REQUEST_METRICS_STATE);
    if (state == null) {
      logger.debug(
          "No state present when ending context {}. Cannot reset RPC request metrics.", context);
    }
    clientDurationHistogram.record(
        TimeUnit.MILLISECONDS.convert(
            endNanos - state.startTimeNanos(), TimeUnit.NANOSECONDS),
        MetricsView.applyRpcView(state.startAttributes(), endAttributes), context);
  }

  @AutoValue
  abstract static class State {

    abstract Attributes startAttributes();

    abstract long startTimeNanos();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.jms.MessagePropertyGetter;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;

public final class JmsSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jms-1.1";

  private static final Instrumenter<MessageWithDestination, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<MessageWithDestination, Void> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    JmsInstrumenterFactory factory =
        new JmsInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());

    PRODUCER_INSTRUMENTER = factory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = factory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = factory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<MessageWithDestination, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<MessageWithDestination, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  public static void createReceiveSpan(
      MessageWithDestination request, Timer timer, Throwable throwable) {
    ContextPropagators propagators = GlobalOpenTelemetry.getPropagators();
    boolean receiveInstrumentationEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    Context parentContext = Context.current();
    if (!receiveInstrumentationEnabled) {
      parentContext =
          propagators
              .getTextMapPropagator()
              .extract(parentContext, request, MessagePropertyGetter.INSTANCE);
    }

    if (consumerReceiveInstrumenter().shouldStart(parentContext, request)) {
      InstrumenterUtil.startAndEnd(
          consumerReceiveInstrumenter(),
          parentContext,
          request,
          null,
          throwable,
          timer.startTime(),
          timer.now());
    }
  }

  private JmsSingletons() {}
}

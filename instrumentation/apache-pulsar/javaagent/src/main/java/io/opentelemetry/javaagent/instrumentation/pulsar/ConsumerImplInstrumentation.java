/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.ClientEnhanceInfo;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.MessageEnhanceInfo;
import io.opentelemetry.javaagent.instrumentation.pulsar.textmap.MessageTextMapGetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.ConsumerImpl;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;

import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.CONSUMER_NAME;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SERVICE_URL;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SUBSCRIPTION;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.TOPIC;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ConsumerImplInstrumentation implements TypeInstrumentation {
  private static final Tracer TRACER = PulsarTelemetry.tracer();
  private static final TextMapPropagator PROPAGATOR = PulsarTelemetry.propagator();

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.ConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    String klassName = ConsumerImplInstrumentation.class.getName();

    transformer.applyAdviceToMethod(
        isConstructor(),
        klassName + "$ConsumerImplConstructorAdviser"
    );

    transformer.applyAdviceToMethod(isMethod()
            .and(isProtected())
            .and(named("messageProcessed"))
            .and(takesArgument(0, named("org.apache.pulsar.client.api.Message"))),
        klassName + "$ConsumerImplMethodAdviser");
  }


  @SuppressWarnings("unused")
  public static class ConsumerImplConstructorAdviser {

    @Advice.OnMethodEnter
    public void before(@Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) PulsarClient client,
        @Advice.Argument(value = 1) String topic) {

      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();

      ClientEnhanceInfo info = new ClientEnhanceInfo(topic, url);
      ClientEnhanceInfo.virtualField(ConsumerImpl.class, consumer, info);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerImplMethodAdviser {

    @Advice.OnMethodEnter
    public void before(@Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message) {
      ClientEnhanceInfo info = ClientEnhanceInfo.virtualField(ConsumerImpl.class, consumer);
      if (null == info) {
        return;
      }

      MessageImpl<?> messageImpl = (MessageImpl<?>) message;
      Context context =
          PROPAGATOR.extract(Context.current(), messageImpl, MessageTextMapGetter.INSTANCE);

      Span span = TRACER.spanBuilder("Pulsar://ConsumerImpl/messageProcessed")
          .setParent(context)
          .setSpanKind(SpanKind.CONSUMER)
          .setAttribute(SERVICE_URL, info.getUrl())
          .setAttribute(TOPIC, info.getTopic())
          .setAttribute(SUBSCRIPTION, consumer.getSubscription())
          .setAttribute(CONSUMER_NAME, consumer.getConsumerName())
          .startSpan();
    }

    @Advice.OnMethodExit
    public void after(@Advice.This ConsumerImpl<?> consumer,
        @Advice.Argument(value = 0) Message<?> message,
        @Advice.Thrown Throwable t) {
      ClientEnhanceInfo info = ClientEnhanceInfo.virtualField(ConsumerImpl.class, consumer);
      if (null == info) {
        return;
      }

      MessageEnhanceInfo messageEnhanceInfo = MessageEnhanceInfo.virtualField(message);
      if (null != messageEnhanceInfo) {
        messageEnhanceInfo
            .setFields(Context.current(), consumer.getTopic(), message.getMessageId());
      }

      Span span = Span.current();
      if (t != null) {
        span.recordException(t);
      }

      span.end();
    }
  }

}

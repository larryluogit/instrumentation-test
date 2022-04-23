/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.ClientEnhanceInfo;
import io.opentelemetry.javaagent.instrumentation.pulsar.textmap.MessageTextMapSetter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.impl.MessageIdImpl;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.ProducerImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.SendCallback;
import java.util.concurrent.CompletableFuture;

import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.MESSAGE_ID;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.SERVICE_URL;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.TOPIC;
import static io.opentelemetry.javaagent.instrumentation.pulsar.PulsarTelemetry.PRODUCER_NAME;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ProducerImplInstrumentation implements TypeInstrumentation {
  private static final Tracer TRACER = PulsarTelemetry.tracer();
  private static final TextMapPropagator PROPAGATOR = PulsarTelemetry.propagator();


  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.ProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(1, String.class)),
        ProducerImplInstrumentation.class.getName() + "$ProducerImplConstructorAdviser");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendAsync"))
            .and(takesArgument(1, named("org.apache.pulsar.client.impl.SendCallback"))),
        ProducerImplInstrumentation.class.getName() + "$ProducerSendAsyncMethodAdviser");
  }

  @SuppressWarnings("unused")
  public static class ProducerImplConstructorAdviser {

    @Advice.OnMethodEnter
    public void intercept(@Advice.This ProducerImpl<?> producer,
        @Argument(value = 0) PulsarClient client,
        @Advice.Argument(value = 1) String topic) {
      PulsarClientImpl pulsarClient = (PulsarClientImpl) client;
      String url = pulsarClient.getLookup().getServiceUrl();
      ClientEnhanceInfo info = new ClientEnhanceInfo(topic, url);
      ClientEnhanceInfo.virtualField(ProducerImpl.class, producer, info);
    }

  }

  @SuppressWarnings("unused")
  public static class ProducerSendAsyncMethodAdviser {

    @Advice.OnMethodEnter
    public void before(@Advice.This ProducerImpl<?> producer,
        @Advice.AllArguments(readOnly = false) Object[] allArguments) {
      ClientEnhanceInfo info = ClientEnhanceInfo.virtualField(ProducerImpl.class, producer);
      if (null == info) {
        return;
      }

      MessageImpl<?> messageImpl = (MessageImpl<?>) allArguments[0];
      Span span = TRACER.spanBuilder("Pulsar://Producer/sendAsync")
          .setParent(Context.current())
          .setSpanKind(SpanKind.PRODUCER)
          .setAttribute(SERVICE_URL, info.getUrl())
          .setAttribute(TOPIC, info.getTopic())
          .setAttribute(PRODUCER_NAME, producer.getProducerName())
          .startSpan();

      Context current = Context.current();
      PROPAGATOR.inject(current, messageImpl, MessageTextMapSetter.INSTANCE);

      MessageImpl<?> message = (MessageImpl<?>) allArguments[0];
      SendCallback callback = (SendCallback) allArguments[1];
      allArguments[1] = new SendCallbackWrapper(info.getTopic(), current, message, callback);
    }

    @Advice.OnMethodExit
    public void after(@Advice.Thrown Throwable t, @Advice.This ProducerImpl<?> producer) {
      ClientEnhanceInfo info = VirtualField.find(ProducerImpl.class, ClientEnhanceInfo.class)
          .get(producer);
      if (null == info) {
        return;
      }

      Span span = Span.current();
      if (null != t) {
        span.recordException(t);
      }

      span.end();
    }
  }


  public static class SendCallbackWrapper implements SendCallback {

    private final String topic;
    private final Context context;
    private final MessageImpl<?> message;
    private final SendCallback delegator;

    public SendCallbackWrapper(String topic, Context context, MessageImpl<?> message,
        SendCallback callback) {
      this.topic = topic;
      this.context = context;
      this.message = message;
      this.delegator = callback;
    }

    @Override
    public void sendComplete(Exception e) {
      SpanBuilder builder = TRACER
          .spanBuilder("Pulsar://Producer/Callback")
          .setParent(this.context)
          .setSpanKind(SpanKind.PRODUCER)
          .setAttribute(TOPIC, topic);

      //set message id
      if (e == null && null != message.getMessageId() && message
          .getMessageId() instanceof MessageIdImpl) {
        MessageIdImpl messageId = (MessageIdImpl) message.getMessageId();
        String midStr = messageId.getLedgerId() + ":" + messageId.getEntryId();
        builder.setAttribute(MESSAGE_ID, midStr);
      }

      Span span = builder.startSpan();

      try {
        this.delegator.sendComplete(e);
      } catch (Exception ex) {
        span.recordException(ex);
      } finally {
        span.end();
      }
    }

    @Override
    public void addCallback(MessageImpl<?> msg, SendCallback scb) {
      this.delegator.addCallback(msg, scb);
    }

    @Override
    public SendCallback getNextSendCallback() {
      return this.delegator.getNextSendCallback();
    }

    @Override
    public MessageImpl<?> getNextMessage() {
      return this.delegator.getNextMessage();
    }

    @Override
    public CompletableFuture<MessageId> getFuture() {
      return this.delegator.getFuture();
    }
  }

}

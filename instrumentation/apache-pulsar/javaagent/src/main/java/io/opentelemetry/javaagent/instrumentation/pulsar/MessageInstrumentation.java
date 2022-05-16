/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.info.MessageEnhanceInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.TopicMessageImpl;

public class MessageInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pulsar.client.impl.MessageImpl")
        .or(named("org.apache.pulsar.client.impl.TopicMessageImpl"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), MessageInstrumentation.class.getName() + "$MessageConstructorAdviser");
  }

  @SuppressWarnings("unused")
  public static class MessageConstructorAdviser {

    @Advice.OnMethodExit
    public static void before(
        @Advice.This Message<?> message, @Advice.AllArguments Object[] allArguments) {
      VirtualField<Message<?>, MessageEnhanceInfo> virtualField =
          VirtualField.find(Message.class, MessageEnhanceInfo.class);

      if (message instanceof MessageImpl) {
        virtualField.set(message, new MessageEnhanceInfo());
      } else {
        Object argument2 = allArguments[2];
        if (message instanceof TopicMessageImpl && argument2 instanceof MessageImpl) {
          MessageImpl<?> impl = (MessageImpl<?>) argument2;
          MessageEnhanceInfo info = virtualField.get(impl);
          virtualField.set(message, info);
        }
      }
    }
  }
}

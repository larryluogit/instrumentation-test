/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jms;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ClientDecorator;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Tracer;
import java.lang.reflect.Method;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.Topic;

public abstract class JMSDecorator extends ClientDecorator {
  public static final JMSDecorator PRODUCER_DECORATE =
      new JMSDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_PRODUCER;
        }
      };

  public static final JMSDecorator CONSUMER_DECORATE =
      new JMSDecorator() {
        @Override
        protected String getSpanType() {
          return SpanTypes.MESSAGE_CONSUMER;
        }
      };

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.jms-1.1");

  @Override
  protected String service() {
    return "jms";
  }

  @Override
  protected String getComponentName() {
    return "jms";
  }

  public String spanNameForReceive(final Message message) {
    return toResourceName(message, null);
  }

  public String spanNameForReceive(final Method method) {
    return "jms." + method.getName();
  }

  public String spanNameForConsumer(final Message message) {
    return toResourceName(message, null);
  }

  public String spanNameForProducer(final Message message, final Destination destination) {
    return toResourceName(message, destination);
  }

  private static final String TIBCO_TMP_PREFIX = "$TMP$";

  public static String toResourceName(final Message message, final Destination destination) {
    Destination jmsDestination = null;
    try {
      jmsDestination = message.getJMSDestination();
    } catch (final Exception e) {
    }
    if (jmsDestination == null) {
      jmsDestination = destination;
    }
    try {
      if (jmsDestination instanceof Queue) {
        final String queueName = ((Queue) jmsDestination).getQueueName();
        if (jmsDestination instanceof TemporaryQueue || queueName.startsWith(TIBCO_TMP_PREFIX)) {
          return "queue/<temporary>";
        } else {
          return "queue/" + queueName;
        }
      }
      if (jmsDestination instanceof Topic) {
        final String topicName = ((Topic) jmsDestination).getTopicName();
        if (jmsDestination instanceof TemporaryTopic || topicName.startsWith(TIBCO_TMP_PREFIX)) {
          return "topic/<temporary>";
        } else {
          return "topic/" + topicName;
        }
      }
    } catch (final Exception e) {
    }
    return "destination";
  }
}

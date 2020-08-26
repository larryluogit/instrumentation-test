/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.jms;

import io.opentelemetry.context.propagation.TextMapPropagator;
import javax.jms.JMSException;
import javax.jms.Message;

public class MessageExtractAdapter implements TextMapPropagator.Getter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

  @Override
  public String get(Message carrier, String key) {
    String propName = key.replace("-", MessageInjectAdapter.DASH);
    Object value;
    try {
      value = carrier.getObjectProperty(propName);
    } catch (JMSException e) {
      throw new RuntimeException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.logging.Logger;

enum MessagePropertySetter implements TextMapSetter<MessageWithDestination> {
  INSTANCE;

  private static final Logger logger = Logger.getLogger(MessagePropertySetter.class.getName());

  static final String DASH = "__dash__";

  @Override
  public void set(MessageWithDestination carrier, String key, String value) {
    String propName = key.replace("-", DASH);
    try {
      carrier.message().setStringProperty(propName, value);
    } catch (Exception e) {
      if (logger.isLoggable(FINE)) {
        logger.log(FINE, "Failure setting jms property: " + propName, e);
      }
    }
  }
}

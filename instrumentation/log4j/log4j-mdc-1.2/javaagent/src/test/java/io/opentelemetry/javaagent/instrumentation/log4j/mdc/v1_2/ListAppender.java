/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.log4j.mdc.v1_2;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

public class ListAppender extends AppenderSkeleton {

  private static final List<LoggingEvent> events = new ArrayList<LoggingEvent>();

  @Override
  protected void append(LoggingEvent loggingEvent) {
    events.add(loggingEvent);
  }

  @Override
  public boolean requiresLayout() {
    return false;
  }

  @Override
  public void close() {}

  public static void clearEvents() {
    events.clear();
  }

  public static List<LoggingEvent> getEvents() {
    return events;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.InstrumentationSpecification
import io.opentelemetry.auto.test.utils.TraceUtils
import io.opentelemetry.instrumentation.log4j.v2_13_2.ListAppender
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.TracingContextUtils
import org.apache.logging.log4j.LogManager

abstract class Log4j2Test extends InstrumentationSpecification {
  def cleanup() {
    ListAppender.get().clearEvents()
  }

  def "no ids when no span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    logger.info("log message 1")
    logger.info("log message 2")

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 2
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue("traceId") == null
    events[0].getContextData().getValue("spanId") == null
    events[0].getContextData().getValue("traceFlags") == null

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue("traceId") == null
    events[1].getContextData().getValue("spanId") == null
    events[1].getContextData().getValue("traceFlags") == null
  }

  def "ids when span"() {
    given:
    def logger = LogManager.getLogger("TestLogger")

    when:
    Span span1
    TraceUtils.runUnderTrace("test") {
      span1 = TracingContextUtils.currentSpan
      logger.info("log message 1")
    }

    logger.info("log message 2")

    Span span2
    TraceUtils.runUnderTrace("test 2") {
      span2 = TracingContextUtils.currentSpan
      logger.info("log message 3")
    }

    def events = ListAppender.get().getEvents()

    then:
    events.size() == 3
    events[0].message.formattedMessage == "log message 1"
    events[0].getContextData().getValue("traceId") == span1.context.traceIdAsHexString
    events[0].getContextData().getValue("spanId") == span1.context.spanIdAsHexString
    events[0].getContextData().getValue("sampled") == "true"

    events[1].message.formattedMessage == "log message 2"
    events[1].getContextData().getValue("traceId") == null
    events[1].getContextData().getValue("spanId") == null
    events[1].getContextData().getValue("sampled") == null

    events[2].message.formattedMessage == "log message 3"
    events[2].getContextData().getValue("traceId") == span2.context.traceIdAsHexString
    events[2].getContextData().getValue("spanId") == span2.context.spanIdAsHexString
    events[2].getContextData().getValue("sampled") == "true"
  }
}

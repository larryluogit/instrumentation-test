/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.api.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.opentelemetry.api.trace.attributes.SemanticAttributes

class TracingRequestWrapperTest extends TracingRequestWrapperTestBase {

  static class TestRequestHandlerString implements RequestHandler<String, String> {

    @Override
    String handleRequest(String input, Context context) {
      if (input == "hello") {
        return "world"
      }
      throw new IllegalArgumentException("bad argument")
    }
  }

  def "handler string traced"() {
    given:
    setLambda(TestRequestHandlerString.getName()+"::handleRequest", TracingRequestWrapper)

    when:
    def result = wrapper.handleRequest("hello", context)

    then:
    result == "world"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler with exception"() {
    given:
    setLambda(TestRequestHandlerString.getName()+"::handleRequest", TracingRequestWrapper)

    when:
    def thrown
    try {
      wrapper.handleRequest("goodbye", context)
    } catch (Throwable t) {
      thrown = t
    }

    then:
    thrown != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name("my_function")
          kind SERVER
          errored true
          errorEvent(IllegalArgumentException, "bad argument")
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }
}

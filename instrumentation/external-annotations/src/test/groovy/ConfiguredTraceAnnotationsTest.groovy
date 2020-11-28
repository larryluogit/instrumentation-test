/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.test.annotation.SayTracedHello
import java.util.concurrent.Callable

class ConfiguredTraceAnnotationsTest extends AgentTestRunner {

  def "method with disabled NewRelic annotation should be ignored"() {
    setup:
    SayTracedHello.fromCallableWhenDisabled()

    expect:
    TEST_WRITER.traces == []
  }

  def "method with custom annotation should be traced"() {
    expect:
    new AnnotationTracedCallable().call() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "AnnotationTracedCallable.call"
          attributes {
          }
        }
      }
    }
  }

  // TODO rewrite as unit test
//  def "test configuration #value"() {
//    setup:
//    def previousConfig = ConfigUtils.updateConfig {
//      if (value) {
//        it.setProperty("otel.instrumentation.external-annotations.include", value)
//      } else {
//        it.remove("otel.instrumentation.external.annotations.include")
//      }
//    }
//
//    expect:
//    TraceAnnotationsInstrumentationModule.AnnotatedMethodsInstrumentation.configureAdditionalTraceAnnotations() == expected.toSet()
//
//    cleanup:
//    ConfigUtils.setConfig(previousConfig)
//
//    where:
//    value                               | expected
//    null                                | DEFAULT_ANNOTATIONS.toList()
//    " "                                 | []
//    "some.Invalid[]"                    | []
//    "some.package.ClassName "           | ["some.package.ClassName"]
//    " some.package.Class\$Name"         | ["some.package.Class\$Name"]
//    "  ClassName  "                     | ["ClassName"]
//    "ClassName"                         | ["ClassName"]
//    "Class\$1;Class\$2;"                | ["Class\$1", "Class\$2"]
//    "Duplicate ;Duplicate ;Duplicate; " | ["Duplicate"]
//  }

  static class AnnotationTracedCallable implements Callable<String> {
    @OuterClass.InterestingMethod
    @Override
    String call() throws Exception {
      return "Hello!"
    }
  }
}

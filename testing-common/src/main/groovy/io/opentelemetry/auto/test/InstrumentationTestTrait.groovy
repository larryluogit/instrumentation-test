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

package io.opentelemetry.auto.test

import com.google.common.base.Predicate
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert
import io.opentelemetry.sdk.trace.data.SpanData

/**
 * A trait which initializes instrumentation library tests, including a test span exporter. All
 * library tests should implement this trait.
 */
trait InstrumentationTestTrait {

  static InstrumentationTestRunner instrumentationTestRunner
  static InMemoryExporter testWriter

  def setupSpec() {
    instrumentationTestRunner = new InstrumentationTestRunnerImpl()
    testWriter = InstrumentationTestRunner.TEST_WRITER

    childSetupSpec()
  }

  def setup() {
    instrumentationTestRunner.beforeTest()

    childSetup()
  }

  /**
   * Initialization method called once per test class. Equivalent to Spock's {@code setupSpec} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetupSpec() {}

  /**
   * Initialization method called once per individual test. Equivalent to Spock's {@code setup} which
   * we can't use because of https://stackoverflow.com/questions/56464191/public-groovy-method-must-be-public-says-the-compiler
   */
  def childSetup() {}

  void assertTraces(final int size,
                    @ClosureParams(
                      value = SimpleType,
                      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
                    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
                    final Closure spec) {
    instrumentationTestRunner.assertTraces(size, spec)
  }

  void assertTracesWithFilter(
    final int size,
    final Predicate<List<SpanData>> excludes,
    @ClosureParams(
      value = SimpleType,
      options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
    @DelegatesTo(value = InMemoryExporterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    instrumentationTestRunner.assertTracesWithFilter(size, spec)
  }

  static class InstrumentationTestRunnerImpl extends InstrumentationTestRunner {}
}

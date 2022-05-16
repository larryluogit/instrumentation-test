/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class FunctionTimerSecondsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer1shim";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  final TestTimer timerObj = new TestTimer();

  @BeforeEach
  void cleanupMeters() {
    timerObj.reset();
    Metrics.globalRegistry.forEachMeter(Metrics.globalRegistry::remove);
  }

  @Test
  void testFunctionTimerWithBaseUnitSeconds() throws InterruptedException {
    // given
    FunctionTimer functionTimer =
        FunctionTimer.builder(
                "testFunctionTimerSeconds",
                timerObj,
                TestTimer::getCount,
                TestTimer::getTotalTimeNanos,
                TimeUnit.NANOSECONDS)
            .description("This is a test function timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timerObj.add(42, TimeUnit.SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testFunctionTimerSeconds.count",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test function timer")
                        .hasUnit("1")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1)
                                                .hasAttributesSatisfying(
                                                    equalTo(
                                                        AttributeKey.stringKey("tag"),
                                                        "value"))))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testFunctionTimerSeconds.sum",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("This is a test function timer")
                        .hasUnit("s")
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(42)
                                            .hasAttributesSatisfying(
                                                equalTo(
                                                    AttributeKey.stringKey("tag"), "value"))))));

    // when
    Metrics.globalRegistry.remove(functionTimer);
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testFunctionTimerSeconds.count", AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testFunctionTimerSeconds.sum", AbstractIterableAssert::isEmpty);
  }
}

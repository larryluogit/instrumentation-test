/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.IntervalTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.OneTimeTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TaskWithError;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TriggerTask;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.EnhancedClassTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.IntervalTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.LambdaTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.OneTimeTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.TaskWithErrorConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config.TriggerTaskConfig;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.service.LambdaTaskConfigurer;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringSchedulingTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void scheduleOneTimeTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(OneTimeTaskConfig.class)) {
      OneTimeTask task = context.getBean(OneTimeTask.class);
      task.blockUntilExecute();

      assertThat(task).isNotNull();
      assertThat(testing.waitForTraces(0)).isEmpty();
    }
  }

  @Test
  void scheduleCronExpressionTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TriggerTaskConfig.class)) {
      TriggerTask task = context.getBean(TriggerTask.class);
      task.blockUntilExecute();

      assertThat(task).isNotNull();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TriggerTask.run")
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(AttributeKey.stringKey("job.system"), "spring_scheduling"),
                              equalTo(
                                  AttributeKey.stringKey("code.namespace"),
                                  TriggerTask.class.getName()),
                              equalTo(AttributeKey.stringKey("code.function"), "run"))));
    }
  }

  @Test
  void scheduleIntervalTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(IntervalTaskConfig.class)) {
      IntervalTask task = context.getBean(IntervalTask.class);
      task.blockUntilExecute();

      assertThat(task).isNotNull();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("IntervalTask.run")
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(AttributeKey.stringKey("job.system"), "spring_scheduling"),
                              equalTo(
                                  AttributeKey.stringKey("code.namespace"),
                                  IntervalTask.class.getName()),
                              equalTo(AttributeKey.stringKey("code.function"), "run"))));
    }
  }

  @Test
  void scheduleLambdaTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(LambdaTaskConfig.class)) {
      LambdaTaskConfigurer configurer = context.getBean(LambdaTaskConfigurer.class);
      configurer.singleUseLatch.await(2000, TimeUnit.MILLISECONDS);

      assertThat(configurer).isNotNull();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("LambdaTaskConfigurer$$Lambda.run")
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(AttributeKey.stringKey("job.system"), "spring_scheduling"),
                              equalTo(AttributeKey.stringKey("code.function"), "run"),
                              satisfies(
                                  AttributeKey.stringKey("code.namespace"),
                                  codeNamespace ->
                                      codeNamespace
                                          .isNotBlank()
                                          .startsWith(
                                              LambdaTaskConfigurer.class.getName()
                                                  + "$$Lambda")))));
    }
  }

  @Test
  void scheduleEnhancedClassTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(EnhancedClassTaskConfig.class)) {
      CountDownLatch latch = context.getBean(CountDownLatch.class);
      latch.await(5, TimeUnit.SECONDS);

      assertThat(latch).isNotNull();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("EnhancedClassTaskConfig.run")
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(AttributeKey.stringKey("job.system"), "spring_scheduling"),
                              equalTo(
                                  AttributeKey.stringKey("code.namespace"),
                                  EnhancedClassTaskConfig.class.getName()),
                              equalTo(AttributeKey.stringKey("code.function"), "run"))));
    }
  }

  @Test
  void taskWithErrorTest() throws InterruptedException {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(TaskWithErrorConfig.class)) {
      TaskWithError task = context.getBean(TaskWithError.class);
      task.blockUntilExecute();

      assertThat(task).isNotNull();
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("TaskWithError.run")
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(AttributeKey.stringKey("job.system"), "spring_scheduling"),
                              equalTo(
                                  AttributeKey.stringKey("code.namespace"),
                                  TaskWithError.class.getName()),
                              equalTo(AttributeKey.stringKey("code.function"), "run"))
                          .hasEventsSatisfyingExactly(
                              event ->
                                  event
                                      .hasName(SemanticAttributes.EXCEPTION_EVENT_NAME)
                                      .hasAttributesSatisfying(
                                          equalTo(
                                              SemanticAttributes.EXCEPTION_TYPE,
                                              IllegalStateException.class.getName()),
                                          equalTo(SemanticAttributes.EXCEPTION_MESSAGE, "failure"),
                                          satisfies(
                                              SemanticAttributes.EXCEPTION_STACKTRACE,
                                              value -> value.isInstanceOf(String.class)))),
                  span -> span.hasName("error-handler").hasParent(trace.getSpan(0))));
    }
  }
}

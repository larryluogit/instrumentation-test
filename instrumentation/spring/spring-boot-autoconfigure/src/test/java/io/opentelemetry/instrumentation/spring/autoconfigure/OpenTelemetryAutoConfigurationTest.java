/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class OpenTelemetryAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customOpenTelemetry() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("customOpenTelemetry")
                    .doesNotHaveBean("openTelemetry")
                    .doesNotHaveBean("sdkTracerProvider")
                    .doesNotHaveBean("openTelemetryResource"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeTracerProviderAndOpenTelemetry() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("openTelemetry")
                    .hasBean("sdkTracerProvider")
                    .hasBean("openTelemetryResource"));
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean but TracerProvider should initialize openTelemetry")
  void initializeOpenTelemetry() {
    this.contextRunner
        .withBean(
            "customTracerProvider",
            SdkTracerProvider.class,
            () -> SdkTracerProvider.builder().build())
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context)
                    .hasBean("openTelemetry")
                    .hasBean("customTracerProvider")
                    .hasBean("openTelemetryResource")
                    .doesNotHaveBean("sdkTracerProvider"));
  }

  @Test
  @DisplayName(
      "when application properties contain entries for resource then Resource bean should contain them")
  void initializeResourceBeanFromProperties() {
    String serviceName = "testService";
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .withPropertyValues("otel.resource.attributes.service.name=" + serviceName)
        .run(
            context -> {
              assertThat(context)
                  .hasBean("openTelemetry")
                  .hasBean("sdkTracerProvider")
                  .hasBean("openTelemetryResource");
              Resource resource = context.getBean("openTelemetryResource", Resource.class);
              assertThat(resource.getAttribute(SERVICE_NAME)).isEqualTo(serviceName);
            });
  }
}

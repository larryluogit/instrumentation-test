/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebClientAutoConfiguration} */
class WebClientAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, WebClientAutoConfiguration.class));

  @Test
  @DisplayName("when httpclients are ENABLED should initialize WebClientBeanPostProccesor bean")
  void httpClientsEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=true")
        .run(
            (context) -> {
              assertThat(
                      context.getBean(
                          "otelWebClientBeanPostProcessor", WebClientBeanPostProcessor.class))
                  .isNotNull();
            });
  }

  @Test
  @DisplayName(
      "when httpclients are DISABLED should NOT initialize WebClientBeanPostProccesor bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.httpclients.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelWebClientBeanPostProcessor")).isFalse();
            });
  }

  @Test
  @DisplayName(
      "when httpclients enabled property is MISSING should initialize WebClientBeanPostProccesor bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(
                  context.getBean(
                      "otelWebClientBeanPostProcessor", WebClientBeanPostProcessor.class))
              .isNotNull();
        });
  }
}

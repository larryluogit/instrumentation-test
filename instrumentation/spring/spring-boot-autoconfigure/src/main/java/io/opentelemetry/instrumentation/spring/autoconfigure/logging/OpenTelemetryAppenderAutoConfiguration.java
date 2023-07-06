/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.logging;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@ConditionalOnBean(OpenTelemetry.class)
public class OpenTelemetryAppenderAutoConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "otel.springboot.log4j-appender",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass({
    io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender.class
  })
  ApplicationListener<ApplicationPreparedEvent> log4jOtelAppenderInitializer(
      OpenTelemetry openTelemetry) {
    return event -> {
      io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender.install(
          openTelemetry);
    };
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "otel.springboot.logback-appender",
      name = "enabled",
      matchIfMissing = true)
  @ConditionalOnClass({
    io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.class
  })
  ApplicationListener<ApplicationPreparedEvent> logbackOtelAppenderInitializer(
      OpenTelemetry openTelemetry) {
    return event -> {
      io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(
          openTelemetry);
    };
  }
}

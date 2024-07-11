/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** convenient annotation to leverage Spring autoconfiguration for OpenTelemetry in non-Spring Boot projects. */
@Configuration
@ComponentScan(basePackages = "io.opentelemetry.instrumentation.spring.autoconfigure")
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableOpenTelemetry {}

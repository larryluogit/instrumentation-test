/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import io.opentelemetry.api.OpenTelemetry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.ParameterNameDiscoverer;

@Aspect
class InstrumentationWithSpanAspect extends WithSpanAspect {

  InstrumentationWithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer) {
    super(
        openTelemetry,
        parameterNameDiscoverer,
        new JoinPointRequest.InstrumentationAnnotationFactory(),
        new WithSpanAspectParameterAttributeNamesExtractor
            .InstrumentationAnnotationAttributeNameSupplier());
  }

  @Override
  @Around("@annotation(io.opentelemetry.instrumentation.annotations.WithSpan)")
  public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
    return super.traceMethod(pjp);
  }
}

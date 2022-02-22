/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create {@link io.opentelemetry.api.trace.Tracer} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
@EnableConfigurationProperties({SamplerProperties.class, ResourceProperties.class})
public class OpenTelemetryAutoConfiguration {

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  public static class OpenTelemetryBeanConfig {

    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider sdkTracerProvider(
        SamplerProperties samplerProperties,
        Resource resource,
        ObjectProvider<List<SpanExporter>> spanExportersProvider) {
      SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();
      tracerProviderBuilder.setResource(resource);

      spanExportersProvider.getIfAvailable(Collections::emptyList).stream()
          .map(spanExporter -> BatchSpanProcessor.builder(spanExporter).build())
          .forEach(tracerProviderBuilder::addSpanProcessor);

      return tracerProviderBuilder
          .setSampler(Sampler.traceIdRatioBased(samplerProperties.getProbability()))
          .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Resource openTelemetryResource(ResourceProperties resourceProperties) {
      AttributesBuilder customAttributesBuilder = Attributes.builder();
      resourceProperties
          .getAttributes()
          .forEach((key, value) -> customAttributesBuilder.put(AttributeKey.stringKey(key), value));
      Resource customResource = Resource.create(customAttributesBuilder.build());
      return Resource.getDefault().merge(customResource);
    }

    @Bean
    public OpenTelemetry openTelemetry(
        ObjectProvider<ContextPropagators> propagatorsProvider, SdkTracerProvider tracerProvider) {

      ContextPropagators propagators = propagatorsProvider.getIfAvailable(ContextPropagators::noop);

      return OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .setPropagators(propagators)
          .build();
    }
  }
}

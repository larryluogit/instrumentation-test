/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Create {@link io.opentelemetry.api.trace.Tracer} bean if bean is missing.
 *
 * <p>Adds span exporter beans to the active tracer provider.
 *
 * <p>Updates the sampler probability for the configured {@link TracerProvider}.
 */
@Configuration
@EnableConfigurationProperties(SamplerProperties.class)
public class OpenTelemetryAutoConfiguration {

  @Configuration
  @ConditionalOnMissingBean(OpenTelemetry.class)
  public static class OpenTelemetryBeanConfig {

    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider sdkTracerProvider(
        SamplerProperties samplerProperties,
        ObjectProvider<List<SpanExporter>> spanExportersProvider,
        Resource otelResource) {
      SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder();

      spanExportersProvider.getIfAvailable(Collections::emptyList).stream()
          .map(spanExporter -> BatchSpanProcessor.builder(spanExporter).build())
          .forEach(tracerProviderBuilder::addSpanProcessor);

      return tracerProviderBuilder
          .setResource(otelResource)
          .setSampler(Sampler.traceIdRatioBased(samplerProperties.getProbability()))
          .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Resource otelResource(
        Environment env, ObjectProvider<List<Supplier<Resource>>> resourceProviders) {
      String applicationName = env.getProperty("spring.application.name");
      Resource resource = defaultResource(applicationName);
      List<Supplier<Resource>> resourceCustomizers =
          resourceProviders.getIfAvailable(Collections::emptyList);
      for (Supplier<Resource> resourceCustomizer : resourceCustomizers) {
        resource = resource.merge(resourceCustomizer.get());
      }
      return resource;
    }

    private static Resource defaultResource(String applicationName) {
      if (applicationName == null) {
        return Resource.getDefault();
      }
      return Resource.getDefault()
          .merge(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, applicationName)));
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

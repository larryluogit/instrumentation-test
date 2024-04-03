/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Optional;

import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import org.springframework.boot.info.BuildProperties;

public class SpringResourceProvider implements ResourceProvider {

  private final Optional<BuildProperties> buildProperties;

  public SpringResourceProvider(Optional<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties;
  }

  @Override
  public Resource createResource(ConfigProperties configProperties) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    buildProperties
        .map(BuildProperties::getName)
        .ifPresent(v -> attributesBuilder.put(ServiceIncubatingAttributes.SERVICE_NAME, v));

    String springApplicationName = configProperties.getString("spring.application.name");
    if (springApplicationName != null) {
      attributesBuilder.put(ServiceIncubatingAttributes.SERVICE_NAME, springApplicationName);
    }

    buildProperties
        .map(BuildProperties::getVersion)
        .ifPresent(v -> attributesBuilder.put(ServiceIncubatingAttributes.SERVICE_VERSION, v));

    return Resource.create(attributesBuilder.build());
  }
}

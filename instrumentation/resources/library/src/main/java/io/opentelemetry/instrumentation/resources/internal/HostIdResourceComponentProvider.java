/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources.internal;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.resources.HostIdResource;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ComponentProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.StructuredConfigProperties;
import io.opentelemetry.sdk.resources.Resource;

/**
 * Declarative config host id resource provider.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("rawtypes")
@AutoService(ComponentProvider.class)
public class HostIdResourceComponentProvider implements ComponentProvider<Resource>, Ordered {
  @Override
  public Class<Resource> getType() {
    return Resource.class;
  }

  @Override
  public String getName() {
    // getName() is unused for Resource ComponentProviders
    return "unused";
  }

  @Override
  public Resource create(StructuredConfigProperties config) {
    return HostIdResource.get();
  }

  @Override
  public int order() {
    // Run after cloud provider resource providers
    return Integer.MAX_VALUE - 1;
  }
}
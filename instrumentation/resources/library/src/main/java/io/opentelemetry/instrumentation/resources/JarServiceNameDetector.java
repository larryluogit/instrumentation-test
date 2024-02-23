/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.logging.Level.FINE;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A {@link ResourceProvider} that will attempt to detect the application name from the jar name.
 */
@AutoService(ResourceProvider.class)
public final class JarServiceNameDetector implements ConditionalResourceProvider {

  private static final Logger logger = Logger.getLogger(JarServiceNameDetector.class.getName());

  private final JarFileDetector jarFileDetector;

  @SuppressWarnings("unused") // SPI
  public JarServiceNameDetector() {
    this(new JarFileDetector());
  }

  // visible for tests
  JarServiceNameDetector(JarFileDetector jarFileDetector) {
    this.jarFileDetector = jarFileDetector;
  }

  @Override
  public Resource createResource(ConfigProperties config) {
    Path jarPath = jarFileDetector.getJarPath();
    if (jarPath == null) {
      return Resource.empty();
    }
    String serviceName = getServiceName(jarPath);
    logger.log(FINE, "Auto-detected service name from the jar file name: {0}", serviceName);
    return Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, serviceName));
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    String serviceName = config.getString("otel.service.name");
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    return serviceName == null
        && !resourceAttributes.containsKey(ResourceAttributes.SERVICE_NAME.getKey())
        && "unknown_service:java".equals(existing.getAttribute(ResourceAttributes.SERVICE_NAME));
  }

  private static String getServiceName(Path jarPath) {
    String jarName = jarPath.getFileName().toString();
    int dotIndex = jarName.lastIndexOf(".");
    return dotIndex == -1 ? jarName : jarName.substring(0, dotIndex);
  }

  @Override
  public int order() {
    // make it run later than the SpringBootServiceNameDetector
    return 1000;
  }
}

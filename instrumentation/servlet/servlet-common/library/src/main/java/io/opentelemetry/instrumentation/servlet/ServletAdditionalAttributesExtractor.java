/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.security.Principal;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ServletAdditionalAttributesExtractor<REQUEST, RESPONSE>
    extends AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletAdditionalAttributesExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  protected void onStart(
      AttributesBuilder attributes, ServletRequestContext<REQUEST> requestContext) {}

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseServletResponseContext,
      @Nullable Throwable error) {
    Principal principal = accessor.getRequestUserPrincipal(requestContext.request());
    if (principal != null) {
      set(attributes, SemanticAttributes.ENDUSER_ID, principal.getName());
    }
  }
}

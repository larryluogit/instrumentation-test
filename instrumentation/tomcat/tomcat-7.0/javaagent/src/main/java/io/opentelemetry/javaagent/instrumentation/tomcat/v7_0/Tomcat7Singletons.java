/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.servlet.v3_0.Servlet3Accessor;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatInstrumenterBuilder;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class Tomcat7Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-7.0";
  private static final Instrumenter<Request, Response> INSTRUMENTER =
      TomcatInstrumenterBuilder.newInstrumenter(
          INSTRUMENTATION_NAME, Servlet3Accessor.INSTANCE, Tomcat7ServletEntityProvider.INSTANCE);

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private Tomcat7Singletons() {}
}

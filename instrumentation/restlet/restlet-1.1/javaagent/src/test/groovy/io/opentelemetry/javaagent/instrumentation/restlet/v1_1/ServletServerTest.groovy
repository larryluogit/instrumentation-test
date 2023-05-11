/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1


import io.opentelemetry.instrumentation.restlet.v1_1.AbstractServletServerTest
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint

class ServletServerTest extends AbstractServletServerTest implements AgentTestTrait {

  @Override
  boolean hasResponseCustomizer(ServerEndpoint endpoint) {
    return true
  }

}

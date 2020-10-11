/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.auto.test.AgentTestTrait
import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2ClientTest
import software.amazon.awssdk.core.client.builder.SdkClientBuilder

class Aws2ClientTest extends AbstractAws2ClientTest implements AgentTestTrait {
  @Override
  void configureSdkClient(SdkClientBuilder builder) {
  }
}

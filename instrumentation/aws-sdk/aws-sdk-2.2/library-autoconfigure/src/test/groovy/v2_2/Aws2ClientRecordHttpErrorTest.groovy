/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package v2_2

import io.opentelemetry.instrumentation.awssdk.v2_2.AbstractAws2ClientRecordHttpErrorTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

class Aws2ClientRecordHttpErrorTest extends AbstractAws2ClientRecordHttpErrorTest implements LibraryTestTrait {
  @Override
  ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder() {
    return ClientOverrideConfiguration.builder()
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.instrumentor

import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractAws1ClientTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class Aws1ClientTest extends AbstractAws1ClientTest implements LibraryTestTrait {
  @Override
  def configureClient(def client) {
    return client
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpAttributes {

  // FIXME: remove this class and replace its usages with SemanticAttributes once schema 1.22 is
  // released

  public static final AttributeKey<String> ERROR_TYPE = stringKey("error.type");

  public static final AttributeKey<Long> HTTP_REQUEST_RESEND_COUNT =
      longKey("http.request.resend_count");

  private HttpAttributes() {}
}

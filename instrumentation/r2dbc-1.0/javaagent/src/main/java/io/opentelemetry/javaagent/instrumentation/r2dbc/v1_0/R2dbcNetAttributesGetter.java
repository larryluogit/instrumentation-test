/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class R2dbcNetAttributesGetter
    implements NetClientAttributesGetter<DbExecution, Void> {

  @Nullable
  @Override
  public String getTransport(DbExecution request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(DbExecution request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getPeerPort(DbExecution request) {
    return request.getPort();
  }
}

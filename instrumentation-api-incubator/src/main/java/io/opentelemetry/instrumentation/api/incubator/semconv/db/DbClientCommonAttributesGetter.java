/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST> {

  @Nullable
  String getSystem(REQUEST request);

  @Deprecated
  @Nullable
  String getUser(REQUEST request);

  /**
   * @deprecated Use {@link #getNamespace(Object)} instead.
   */
  @Deprecated
  @Nullable
  String getName(REQUEST request);

  @Nullable
  String getNamespace(REQUEST request);

  @Deprecated
  @Nullable
  String getConnectionString(REQUEST request);
}

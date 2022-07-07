/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import javax.annotation.Nullable;

/**
 * An interface for getting server-based network attributes. It adapts an instrumentation-specific
 * request type into the 3 common attributes (transport, peerPort, peerName).
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * server library/framework. It will be used by the {@link NetServerAttributesExtractor} to obtain
 * the various network attributes in a type-generic way.
 */
public interface NetServerAttributesGetter<REQUEST> {

  @Nullable
  String transport(REQUEST request);

  @Nullable
  Integer sockPeerPort(REQUEST request);

  @Nullable
  String sockPeerAddr(REQUEST request);
}

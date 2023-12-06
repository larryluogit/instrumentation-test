/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcAttributeGetter;

enum DubboRpcAttributeGetter implements RpcAttributeGetter<DubboRequest> {
  INSTANCE;

  @Override
  public String getSystem(DubboRequest request) {
    return "apache_dubbo";
  }

  @Override
  public String getService(DubboRequest request) {
    return request.invocation().getInvoker().getInterface().getName();
  }

  @Override
  public String getMethod(DubboRequest request) {
    return request.invocation().getMethodName();
  }
}

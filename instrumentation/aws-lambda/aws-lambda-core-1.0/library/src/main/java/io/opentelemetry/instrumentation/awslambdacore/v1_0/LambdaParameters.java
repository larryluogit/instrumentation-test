/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import com.amazonaws.services.lambda.runtime.Context;
import java.lang.reflect.Method;

final class LambdaParameters {

  static <T> Object[] toArray(Method targetMethod, T input, Context context) {
    Class<?>[] parameterTypes = targetMethod.getParameterTypes();
    Object[] parameters = new Object[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      Class<?> clazz = parameterTypes[i];
      boolean isContext = clazz.equals(Context.class);
      if (isContext) {
        parameters[i] = context;
      } else if (i == 0) {
        parameters[0] = input;
      }
    }
    return parameters;
  }

  private LambdaParameters() {}
}

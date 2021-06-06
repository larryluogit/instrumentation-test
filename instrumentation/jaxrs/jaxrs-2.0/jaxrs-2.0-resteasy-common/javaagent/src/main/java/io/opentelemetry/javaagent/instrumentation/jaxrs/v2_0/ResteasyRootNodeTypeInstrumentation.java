/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.core.ResourceLocatorInvoker;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public class ResteasyRootNodeTypeInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.core.registry.RootNode");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("addInvoker")
            .and(takesArgument(0, String.class))
            // package of ResourceInvoker was changed in reasteasy 4
            .and(
                takesArgument(
                    1,
                    namedOneOf(
                        "org.jboss.resteasy.core.ResourceInvoker",
                        "org.jboss.resteasy.spi.ResourceInvoker"))),
        ResteasyRootNodeTypeInstrumentation.class.getName() + "$AddInvokerAdvice");
  }

  public static class AddInvokerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInvoker(
        @Advice.Argument(0) String path,
        @Advice.Argument(value = 1, typing = Assigner.Typing.DYNAMIC) Object invoker) {
      String normalizedPath = JaxRsPathUtil.normalizePath(path);
      if (invoker instanceof ResourceLocatorInvoker) {
        ResourceLocatorInvoker resourceLocatorInvoker = (ResourceLocatorInvoker) invoker;
        InstrumentationContext.get(ResourceLocatorInvoker.class, String.class)
            .put(resourceLocatorInvoker, normalizedPath);
      } else if (invoker instanceof ResourceMethodInvoker) {
        ResourceMethodInvoker resourceMethodInvoker = (ResourceMethodInvoker) invoker;
        InstrumentationContext.get(ResourceMethodInvoker.class, String.class)
            .put(resourceMethodInvoker, normalizedPath);
      }
    }
  }
}

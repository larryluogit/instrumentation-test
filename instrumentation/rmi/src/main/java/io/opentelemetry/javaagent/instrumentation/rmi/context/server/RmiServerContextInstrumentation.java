/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.server;

import static io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPropagator.CONTEXT_CALL_ID;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Target;

public final class RmiServerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("sun.rmi.transport.ObjectTable"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isStatic())
            .and(named("getTarget"))
            .and((takesArgument(0, named("sun.rmi.transport.ObjectEndpoint")))),
        getClass().getName() + "$ObjectTableAdvice");
  }

  public static class ObjectTableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) Object oe, @Advice.Return(readOnly = false) Target result) {
      // comparing toString() output allows us to avoid using reflection to be able to compare
      // ObjID and ObjectEndpoint objects
      // ObjectEndpoint#toString() only returns this.objId.toString() value which is exactly
      // what we're interested in here.
      if (!CONTEXT_CALL_ID.toString().equals(oe.toString())) {
        return;
      }
      result = ContextDispatcher.newDispatcherTarget();
    }
  }
}

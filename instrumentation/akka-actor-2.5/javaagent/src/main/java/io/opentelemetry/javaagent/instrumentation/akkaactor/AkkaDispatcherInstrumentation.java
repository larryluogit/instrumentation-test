/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.Envelope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AkkaDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("akka.dispatch.Dispatcher");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("dispatch")
            .and(takesArgument(0, named("akka.actor.ActorCell")))
            .and(takesArgument(1, named("akka.dispatch.Envelope"))),
        AkkaDispatcherInstrumentation.class.getName() + "$DispatcherStateAdvice");
  }

  public static class DispatcherStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterDispatch(@Advice.Argument(1) Envelope envelope) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(envelope)) {
        ContextStore<Envelope, State> contextStore =
            InstrumentationContext.get(Envelope.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, envelope, Java8BytecodeBridge.currentContext());
      }
      return null;
    }
  }
}

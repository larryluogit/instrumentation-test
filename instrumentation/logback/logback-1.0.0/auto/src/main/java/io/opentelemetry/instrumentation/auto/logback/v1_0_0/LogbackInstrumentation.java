/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.logback.v1_0_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class LogbackInstrumentation extends Instrumenter.Default {

  public LogbackInstrumentation() {
    super("logback");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("ch.qos.logback.classic.Logger");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("ch.qos.logback.classic.spi.ILoggingEvent", Span.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("callAppenders"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("ch.qos.logback.classic.spi.ILoggingEvent"))),
        LogbackInstrumentation.class.getName() + "$CallAppendersAdvice");
  }

  public static class CallAppendersAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(value = 0, readOnly = false) ILoggingEvent event) {
      InstrumentationContext.get(ILoggingEvent.class, Span.class)
          .put(event, TracingContextUtils.getCurrentSpan());
    }
  }
}

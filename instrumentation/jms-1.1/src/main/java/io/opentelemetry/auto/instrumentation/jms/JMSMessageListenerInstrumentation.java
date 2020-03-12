/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.jms;

import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.CONSUMER_DECORATE;
import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jms.MessageExtractAdapter.GETTER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.classLoaderHasNoResources;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.util.Map;
import javax.jms.Message;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSMessageListenerInstrumentation extends Instrumenter.Default {

  public JMSMessageListenerInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return not(classLoaderHasNoResources("javax/jms/MessageListener.class"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageListener"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".JMSDecorator",
      packageName + ".JMSDecorator$1",
      packageName + ".JMSDecorator$2",
      packageName + ".MessageExtractAdapter",
      packageName + ".MessageInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("onMessage").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        JMSMessageListenerInstrumentation.class.getName() + "$MessageListenerAdvice");
  }

  public static class MessageListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final Message message, @Advice.This final MessageListener listener) {

      final Span.Builder spanBuilder = TRACER.spanBuilder("jms.onMessage").setSpanKind(CONSUMER);
      try {
        final SpanContext extractedContext = TRACER.getHttpTextFormat().extract(message, GETTER);
        spanBuilder.setParent(extractedContext);
      } catch (final IllegalArgumentException e) {
        // Couldn't extract a context. We should treat this as a root span.
        spanBuilder.setNoParent();
      }

      final Span span = spanBuilder.startSpan();
      span.setAttribute("span.origin.type", listener.getClass().getName());
      CONSUMER_DECORATE.afterStart(span);
      CONSUMER_DECORATE.onReceive(span, message);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      final Span span = spanWithScope.getSpan();
      CONSUMER_DECORATE.onError(span, throwable);
      CONSUMER_DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}

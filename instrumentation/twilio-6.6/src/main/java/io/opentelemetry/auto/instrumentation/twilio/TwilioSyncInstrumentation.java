package io.opentelemetry.auto.instrumentation.twilio;

import static io.opentelemetry.auto.instrumentation.twilio.TwilioClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.twilio.TwilioClientDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeExtendsClass;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import com.twilio.Twilio;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a seperate service. */
@AutoService(Instrumenter.class)
public class TwilioSyncInstrumentation extends Instrumenter.Default {

  public TwilioSyncInstrumentation() {
    super("twilio-sdk");
  }

  /** Match any child class of the base Twilio service classes. */
  @Override
  public net.bytebuddy.matcher.ElementMatcher<
          ? super net.bytebuddy.description.type.TypeDescription>
      typeMatcher() {
    return safeExtendsClass(
        named("com.twilio.base.Creator")
            .or(named("com.twilio.base.Deleter"))
            .or(named("com.twilio.base.Fetcher"))
            .or(named("com.twilio.base.Reader"))
            .or(named("com.twilio.base.Updater")));
  }

  /** Return the helper classes which will be available for use in instrumentation. */
  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      packageName + ".TwilioClientDecorator",
    };
  }

  /** Return bytebuddy transformers for instrumenting the Twilio SDK. */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    /*
       We are listing out the main service calls on the Creator, Deleter, Fetcher, Reader, and
       Updater abstract classes. The isDeclaredBy() matcher did not work in the unit tests and
       we found that there were certain methods declared on the base class (particularly Reader),
       which we weren't interested in annotating.
    */

    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(not(isAbstract()))
            .and(
                named("create")
                    .or(named("delete"))
                    .or(named("read"))
                    .or(named("fetch"))
                    .or(named("update"))),
        TwilioSyncInstrumentation.class.getName() + "$TwilioClientAdvice");
  }

  /** Advice for instrumenting Twilio service classes. */
  public static class TwilioClientAdvice {

    /** Method entry instrumentation. */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.This final Object that, @Advice.Origin("#m") final String methodName) {

      // Ensure that we only create a span for the top-level Twilio client method; except in the
      // case of async operations where we want visibility into how long the task was delayed from
      // starting. Our call depth checker does not span threads, so the async case is handled
      // automatically for us.
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Twilio.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span = TRACER.spanBuilder("twilio.sdk").setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onServiceExecution(span, that, methodName);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Object response) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(Twilio.class);

      // If we have a scope (i.e. we were the top-level Twilio SDK invocation),
      try {
        final Span span = spanWithScope.getSpan();

        DECORATE.onResult(span, response);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      } finally {
        spanWithScope.closeScope();
      }
    }
  }
}

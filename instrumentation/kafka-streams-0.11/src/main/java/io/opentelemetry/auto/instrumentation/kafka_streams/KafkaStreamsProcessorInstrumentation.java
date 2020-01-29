package io.opentelemetry.auto.instrumentation.kafka_streams;

import static io.opentelemetry.auto.instrumentation.kafka_streams.KafkaStreamsDecorator.CONSUMER_DECORATE;
import static io.opentelemetry.auto.instrumentation.kafka_streams.KafkaStreamsDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.kafka_streams.KafkaStreamsProcessorInstrumentation.SpanScopeHolder.CURRENT;
import static io.opentelemetry.auto.instrumentation.kafka_streams.TextMapExtractAdapter.GETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.streams.processor.internals.StampedRecord;

public class KafkaStreamsProcessorInstrumentation {
  // These two instrumentations work together to apply StreamTask.process.
  // The combination of these is needed because there's no good instrumentation point.
  // FIXME: this instrumentation takes somewhat strange approach. It looks like Kafka Streams
  // defines notions of 'processor', 'source' and 'sink'. There is no 'consumer' as such.
  // Also this instrumentation doesn't define 'producer' making it 'asymmetric' - resulting
  // in awkward tests and traces. We may want to revisit this in the future.

  public static class SpanScopeHolder {
    public static final ThreadLocal<SpanScopePair> CURRENT = new ThreadLocal<>();
  }

  @AutoService(Instrumenter.class)
  public static class StartInstrumentation extends Instrumenter.Default {

    public StartInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.PartitionGroup");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        "io.opentelemetry.auto.decorator.BaseDecorator",
        "io.opentelemetry.auto.decorator.ClientDecorator",
        packageName + ".KafkaStreamsDecorator",
        packageName + ".TextMapExtractAdapter",
        KafkaStreamsProcessorInstrumentation.class.getName() + "$SpanScopeHolder"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(isPackagePrivate())
              .and(named("nextRecord"))
              .and(returns(named("org.apache.kafka.streams.processor.internals.StampedRecord"))),
          StartInstrumentation.class.getName() + "$StartSpanAdvice");
    }

    public static class StartSpanAdvice {

      @Advice.OnMethodExit(suppress = Throwable.class)
      public static void onExit(@Advice.Return final StampedRecord record) {
        if (record == null) {
          return;
        }

        final Span.Builder spanBuilder = TRACER.spanBuilder("kafka.consume");
        try {
          final SpanContext extractedContext =
              TRACER.getHttpTextFormat().extract(record.value.headers(), GETTER);
          spanBuilder.setParent(extractedContext);
        } catch (final IllegalArgumentException e) {
          // Couldn't extract a context. We should treat this as a root span.
          spanBuilder.setNoParent();
        }
        final Span span = spanBuilder.startSpan();
        CONSUMER_DECORATE.afterStart(span);
        CONSUMER_DECORATE.onConsume(span, record);

        CURRENT.set(new SpanScopePair(span, TRACER.withSpan(span)));
      }
    }
  }

  @AutoService(Instrumenter.class)
  public static class StopInstrumentation extends Instrumenter.Default {

    public StopInstrumentation() {
      super("kafka", "kafka-streams");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.apache.kafka.streams.processor.internals.StreamTask");
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        "io.opentelemetry.auto.decorator.BaseDecorator",
        "io.opentelemetry.auto.decorator.ClientDecorator",
        packageName + ".KafkaStreamsDecorator",
        packageName + ".TextMapExtractAdapter",
        KafkaStreamsProcessorInstrumentation.class.getName() + "$SpanScopeHolder"
      };
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("process")).and(takesArguments(0)),
          StopInstrumentation.class.getName() + "$StopSpanAdvice");
    }

    public static class StopSpanAdvice {

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void stopSpan(@Advice.Thrown final Throwable throwable) {
        // This is dangerous... we assume the span/scope is the one we expect, but it may not be.
        final SpanScopePair spanScopePair = CURRENT.get();
        if (spanScopePair != null) {
          CURRENT.set(null);
          final Span span = spanScopePair.getSpan();
          CONSUMER_DECORATE.onError(span, throwable);
          CONSUMER_DECORATE.beforeFinish(span);
          span.end();
          spanScopePair.getScope().close();
        }
      }
    }
  }
}

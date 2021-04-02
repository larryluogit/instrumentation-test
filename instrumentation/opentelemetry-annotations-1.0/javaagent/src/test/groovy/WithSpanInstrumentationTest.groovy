/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.extension.annotations.WithSpan
import io.opentelemetry.instrumentation.test.utils.TraceUtils
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.SpanKind.SERVER

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.test.annotation.TracedWithSpan
import net.bytebuddy.ByteBuddy
import net.bytebuddy.ClassFileVersion
import net.bytebuddy.asm.MemberAttributeExtension
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.implementation.bind.annotation.This
import net.bytebuddy.matcher.ElementMatchers

/**
 * This test verifies that auto instrumentation supports {@link io.opentelemetry.extension.annotations.WithSpan} contrib annotation.
 */
class WithSpanInstrumentationTest extends AgentInstrumentationSpecification {

  def "should derive automatic name"() {
    setup:
    new TracedWithSpan().otel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.otel"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should take span name from annotation"() {
    setup:
    new TracedWithSpan().namedOtel()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "manualName"
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should take span kind from annotation"() {
    setup:
    new TracedWithSpan().oneOfAKind()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.oneOfAKind"
          kind PRODUCER
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture multiple spans"() {
    setup:
    new TracedWithSpan().server()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "TracedWithSpan.server"
          kind SERVER
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name "TracedWithSpan.otel"
          childOf span(0)
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should not capture multiple server spans"() {
    setup:
    new TracedWithSpan().nestedServers()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.nestedServers"
          kind SERVER
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should not capture multiple client spans"() {
    setup:
    new TracedWithSpan().nestedClients()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.nestedClients"
          kind CLIENT
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should ignore method excluded by trace.annotated.methods.exclude configuration"() {
    setup:
    new TracedWithSpan().ignored()

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

  def "should capture span for already completed CompletionStage"() {
    setup:
    def future = CompletableFuture.completedFuture("Done")
    new TracedWithSpan().completionStage(future)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed CompletionStage"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completionStage(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.complete("Done")

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already exceptionally completed CompletionStage"() {
    setup:
    def future = new CompletableFuture<String>()
    future.completeExceptionally(new IllegalArgumentException("Boom"))
    new TracedWithSpan().completionStage(future)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually exceptionally completed CompletionStage"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completionStage(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.completeExceptionally(new IllegalArgumentException("Boom"))

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for null CompletionStage"() {
    setup:
    new TracedWithSpan().completionStage(null)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completionStage"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already completed CompletableFuture"() {
    setup:
    def future = CompletableFuture.completedFuture("Done")
    new TracedWithSpan().completableFuture(future)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually completed CompletableFuture"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.complete("Done")

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for already exceptionally completed CompletableFuture"() {
    setup:
    def future = new CompletableFuture<String>()
    future.completeExceptionally(new IllegalArgumentException("Boom"))
    new TracedWithSpan().completableFuture(future)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for eventually exceptionally completed CompletableFuture"() {
    setup:
    def future = new CompletableFuture<String>()
    new TracedWithSpan().completableFuture(future)

    expect:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}

    future.completeExceptionally(new IllegalArgumentException("Boom"))

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored true
          errorEvent(IllegalArgumentException, "Boom")
          attributes {
          }
        }
      }
    }
  }

  def "should capture span for null CompletableFuture"() {
    setup:
    new TracedWithSpan().completableFuture(null)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TracedWithSpan.completableFuture"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
      }
    }
  }

  def "instrument java6 class"() {
    setup:
    /*
     class GeneratedJava6TestClass implements Runnable {
       @WithSpan
       public void run() {
         TraceUtils.runUnderTrace("intercept", {})
       }
     }
     */
    Class<?> generatedClass = new ByteBuddy(ClassFileVersion.JAVA_V6)
      .subclass(Object)
      .name("GeneratedJava6TestClass")
      .implement(Runnable)
      .defineMethod("run", void.class, Modifier.PUBLIC).intercept(MethodDelegation.to(new Object() {
        @RuntimeType
        void intercept(@This Object o) {
          TraceUtils.runUnderTrace("intercept", {})
        }
      }))
      .visit(new MemberAttributeExtension.ForMethod()
        .annotateMethod(AnnotationDescription.Builder.ofType(WithSpan).build())
        .on(ElementMatchers.named("run")))
      .make()
      .load(getClass().getClassLoader())
      .getLoaded()

    Runnable runnable = (Runnable) generatedClass.getConstructor().newInstance()
    runnable.run()

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "GeneratedJava6TestClass.run"
          kind SpanKind.INTERNAL
          hasNoParent()
          errored false
          attributes {
          }
        }
        span(1) {
          name "intercept"
          kind SpanKind.INTERNAL
          childOf(span(0))
          errored false
          attributes {
          }
        }
      }
    }
  }
}

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

package io.opentelemetry.auto.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.test.asserts.InMemoryExporterAssert;
import io.opentelemetry.auto.util.test.AgentSpecification;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.javaagent.tooling.matcher.AdditionalLibraryIgnoresMatcher;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.trace.Tracer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.SpecMetadata;

/**
 * A spock test runner which automatically applies instrumentation and exposes a global trace
 * writer.
 *
 * <p>To use, write a regular spock test, but extend this class instead of {@link
 * spock.lang.Specification}. <br>
 * This will cause the following to occur before test startup:
 *
 * <ul>
 *   <li>All {@link Instrumenter}s on the test classpath will be applied. Matching preloaded classes
 *       will be retransformed.
 *   <li>{@link AgentTestRunner#TEST_WRITER} will be registered with the global tracer and available
 *       in an initialized state.
 * </ul>
 */
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
public abstract class AgentTestRunner extends AgentSpecification {

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(AgentTestRunner.class);

  static {
    // always run with the thread propagation debugger to help track down sporadic test failures
    System.setProperty("otel.threadPropagationDebugger", "true");
    System.setProperty("otel.internal.failOnContextLeak", "true");
  }

  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final InMemoryExporter TEST_WRITER;

  protected static final Tracer TEST_TRACER;

  private static final ElementMatcher.Junction<TypeDescription> GLOBAL_LIBRARIES_IGNORES_MATCHER =
      AdditionalLibraryIgnoresMatcher.additionalLibraryIgnoresMatcher();

  protected static final Set<String> TRANSFORMED_CLASSES_NAMES = Sets.newConcurrentHashSet();
  protected static final Set<TypeDescription> TRANSFORMED_CLASSES_TYPES =
      Sets.newConcurrentHashSet();
  private static final AtomicInteger INSTRUMENTATION_ERROR_COUNT = new AtomicInteger(0);
  private static final TestRunnerListener TEST_LISTENER = new TestRunnerListener();

  private static final Instrumentation INSTRUMENTATION;
  private static volatile ClassFileTransformer activeTransformer = null;

  static {
    INSTRUMENTATION = ByteBuddyAgent.getInstrumentation();

    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry")).setLevel(Level.DEBUG);

    TEST_WRITER = new InMemoryExporter();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(TEST_WRITER);
    TEST_TRACER = OpenTelemetry.getTracer("io.opentelemetry.auto");
  }

  protected static Tracer getTestTracer() {
    return TEST_TRACER;
  }

  /**
   * Invoked when Bytebuddy encounters an instrumentation error. Fails the test by default.
   *
   * <p>Override to skip specific expected errors.
   *
   * @return true if the test should fail because of this error.
   */
  protected boolean onInstrumentationError(
      String typeName,
      ClassLoader classLoader,
      JavaModule module,
      boolean loaded,
      Throwable throwable) {
    log.error(
        "Unexpected instrumentation error when instrumenting {} on {}",
        typeName,
        classLoader,
        throwable);
    return true;
  }

  /**
   * @param className name of the class being loaded
   * @param classLoader classloader class is being defined on
   * @return true if the class under load should be transformed for this test.
   */
  protected boolean shouldTransformClass(String className, ClassLoader classLoader) {
    return true;
  }

  public static synchronized void resetInstrumentation() {
    if (null != activeTransformer) {
      INSTRUMENTATION.removeTransformer(activeTransformer);
      activeTransformer = null;
    }
  }

  /**
   * Normally {@code @BeforeClass} is run only on static methods, but spock allows us to run it on
   * instance methods. Note: this means there is a 'special' instance of test class that is not used
   * to run any tests, but instead is just used to run this method once.
   */
  @BeforeClass
  public void setupBeforeTests() {
    if (activeTransformer == null) {
      activeTransformer =
          AgentInstaller.installBytebuddyAgent(INSTRUMENTATION, true, TEST_LISTENER);
    }
    TEST_LISTENER.activateTest(this);
  }

  @Before
  public void beforeTest() {
    assert !getTestTracer().getCurrentSpan().getContext().isValid()
        : "Span is active before test has started: " + getTestTracer().getCurrentSpan();
    TEST_WRITER.clear();
  }

  /** See comment for {@code #setupBeforeTests} above. */
  @AfterClass
  public void cleanUpAfterTests() {
    TEST_LISTENER.deactivateTest(this);
  }

  /**
   * This is used by setupSpec() methods to auto-retry setup that depends on finding and then using
   * an available free port, because that kind of setup can fail sporadically if the available port
   * gets re-used between when we find the available port and when we use it.
   *
   * @param closure the groovy closure to run with retry
   */
  public static void withRetryOnAddressAlreadyInUse(Closure<?> closure) {
    withRetryOnAddressAlreadyInUse(closure, 3);
  }

  private static void withRetryOnAddressAlreadyInUse(Closure<?> closure, int numRetries) {
    try {
      closure.call();
    } catch (Throwable t) {
      // typically this is "java.net.BindException: Address already in use", but also can be
      // "io.netty.channel.unix.Errors$NativeIoException: bind() failed: Address already in use"
      if (numRetries == 0 || !t.getMessage().contains("Address already in use")) {
        throw t;
      }
      log.debug("retrying due to bind exception: {}", t.getMessage(), t);
      withRetryOnAddressAlreadyInUse(closure, numRetries - 1);
    }
  }

  @AfterClass
  public static synchronized void agentCleanup() {
    // Cleanup before assertion.
    assert INSTRUMENTATION_ERROR_COUNT.get() == 0
        : INSTRUMENTATION_ERROR_COUNT.get() + " Instrumentation errors during test";

    List<TypeDescription> ignoredClassesTransformed = new ArrayList<>();
    for (TypeDescription type : TRANSFORMED_CLASSES_TYPES) {
      if (GLOBAL_LIBRARIES_IGNORES_MATCHER.matches(type)) {
        ignoredClassesTransformed.add(type);
      }
    }
    assert ignoredClassesTransformed.isEmpty()
        : "Transformed classes match global libraries ignore matcher: " + ignoredClassesTransformed;
  }

  public static void assertTraces(
      int size,
      @ClosureParams(
              value = SimpleType.class,
              options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
          @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
          Closure spec) {
    InMemoryExporterAssert.assertTraces(
        TEST_WRITER, size, Predicates.<List<SpanData>>alwaysFalse(), spec);
  }

  public static void assertTracesWithFilter(
      int size,
      Predicate<List<SpanData>> excludes,
      @ClosureParams(
              value = SimpleType.class,
              options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
          @DelegatesTo(value = InMemoryExporterAssert.class, strategy = Closure.DELEGATE_FIRST)
          Closure spec) {
    InMemoryExporterAssert.assertTraces(TEST_WRITER, size, excludes, spec);
  }

  public static class TestRunnerListener implements AgentBuilder.Listener {
    private static final List<AgentTestRunner> activeTests = new CopyOnWriteArrayList<>();

    public void activateTest(AgentTestRunner testRunner) {
      activeTests.add(testRunner);
    }

    public void deactivateTest(AgentTestRunner testRunner) {
      activeTests.remove(testRunner);
    }

    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
      for (AgentTestRunner testRunner : activeTests) {
        if (!testRunner.shouldTransformClass(typeName, classLoader)) {
          throw new AbortTransformationException(
              "Aborting transform for class name = " + typeName + ", loader = " + classLoader);
        }
      }
    }

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      TRANSFORMED_CLASSES_NAMES.add(typeDescription.getActualName());
      TRANSFORMED_CLASSES_TYPES.add(typeDescription);
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {}

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
      if (!(throwable instanceof AbortTransformationException)) {
        for (AgentTestRunner testRunner : activeTests) {
          if (testRunner.onInstrumentationError(typeName, classLoader, module, loaded, throwable)) {
            INSTRUMENTATION_ERROR_COUNT.incrementAndGet();
            break;
          }
        }
      }
    }

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

    /** Used to signal that a transformation was intentionally aborted and is not an error. */
    public static class AbortTransformationException extends RuntimeException {
      public AbortTransformationException() {
        super();
      }

      public AbortTransformationException(String message) {
        super(message);
      }
    }
  }

  protected static String getClassName(Class clazz) {
    String className = clazz.getSimpleName();
    if (className.isEmpty()) {
      className = clazz.getName();
      if (clazz.getPackage() != null) {
        String pkgName = clazz.getPackage().getName();
        if (!pkgName.isEmpty()) {
          className = clazz.getName().replace(pkgName, "").substring(1);
        }
      }
    }
    return className;
  }
}

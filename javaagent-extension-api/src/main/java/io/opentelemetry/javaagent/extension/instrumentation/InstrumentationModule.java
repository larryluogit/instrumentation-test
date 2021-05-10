/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.any;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.muzzle.Reference;
import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation module groups several connected {@link TypeInstrumentation}s together, sharing
 * classloader matcher, helper classes, muzzle safety checks, etc. Ideally all types in a single
 * instrumented library should live in a single module.
 *
 * <p>Classes extending {@link InstrumentationModule} should be public and non-final so that it's
 * possible to extend and reuse them in vendor distributions.
 *
 * <p>WARNING: using {@link InstrumentationModule} as SPI is now deprecated; please use {@link
 * AgentExtension} instead.
 */
public abstract class InstrumentationModule implements AgentExtension {
  private static final String[] EMPTY = new String[0];

  private static final boolean DEFAULT_ENABLED =
      Config.get().getBooleanProperty("otel.instrumentation.common.default-enabled", true);

  private final Set<String> instrumentationNames;
  final boolean enabled;

  /**
   * Creates an instrumentation module. Note that all implementations of {@link
   * InstrumentationModule} must have a default constructor (for SPI), so they have to pass the
   * instrumentation names to the super class constructor.
   *
   * <p>The instrumentation names should follow several rules:
   *
   * <ul>
   *   <li>Instrumentation names should consist of hyphen-separated words, e.g. {@code
   *       instrumented-library};
   *   <li>In general, instrumentation names should be the as close as possible to the gradle module
   *       name - which in turn should be as close as possible to the instrumented library name;
   *   <li>The main instrumentation name should be the same as the gradle module name, minus the
   *       version if it's a part of the module name. When several versions of a library are
   *       instrumented they should all share the same main instrumentation name so that it's easy
   *       to enable/disable the instrumentation regardless of the runtime library version;
   *   <li>If the gradle module has a version as a part of its name, an additional instrumentation
   *       name containing the version should be passed, e.g. {@code instrumented-library-1.0}.
   * </ul>
   */
  public InstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    this(toList(mainInstrumentationName, additionalInstrumentationNames));
  }

  /**
   * Creates an instrumentation module.
   *
   * @see #InstrumentationModule(String, String...)
   */
  public InstrumentationModule(List<String> instrumentationNames) {
    if (instrumentationNames.isEmpty()) {
      throw new IllegalArgumentException("InstrumentationModules must be named");
    }
    this.instrumentationNames = new LinkedHashSet<>(instrumentationNames);
    this.enabled = Config.get().isInstrumentationEnabled(instrumentationNames, defaultEnabled());
  }

  private static List<String> toList(String first, String[] rest) {
    List<String> instrumentationNames = new ArrayList<>(rest.length + 1);
    instrumentationNames.add(first);
    instrumentationNames.addAll(asList(rest));
    return instrumentationNames;
  }

  /**
   * Add this instrumentation to an AgentBuilder.
   *
   * @param parentAgentBuilder AgentBuilder to base instrumentation config off of.
   * @return the original agentBuilder and this instrumentation
   */
  @Override
  public final AgentBuilder extend(AgentBuilder parentAgentBuilder) {
    return InstrumentationExtensionImplementation.get().extend(this, parentAgentBuilder);
  }

  @Override
  public final String extensionName() {
    return instrumentationNames.iterator().next();
  }

  /**
   * Returns all helper classes that will be injected into the application classloader, both ones
   * provided by the implementation and ones that were collected by muzzle during compilation.
   */
  public final List<String> getAllHelperClassNames() {
    List<String> helperClassNames = new ArrayList<>();
    helperClassNames.addAll(asList(additionalHelperClassNames()));
    helperClassNames.addAll(asList(getMuzzleHelperClassNames()));
    return helperClassNames;
  }

  /**
   * Instrumentation modules can override this method to specify additional packages (or classes)
   * that should be treated as "library instrumentation" packages. Classes from those packages will
   * be treated by muzzle as instrumentation helper classes: they will be scanned for references and
   * automatically injected into the application classloader if they're used in any type
   * instrumentation. The classes for which this predicate returns {@code true} will be treated as
   * helper classes, in addition to the default ones defined in the {@code
   * InstrumentationClassPredicate} class.
   *
   * @param className The name of the class that may or may not be a helper class.
   */
  public boolean isHelperClass(String className) {
    return false;
  }

  /**
   * Returns a list of references to helper and library classes used in this module's type
   * instrumentation advices.
   *
   * <p>The actual implementation of this method is generated automatically during compilation by
   * the {@code io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * <p><b>This method is generated automatically</b>: if you override it, the muzzle compile plugin
   * will not generate a new implementation, it will leave the existing one.
   */
  public Reference[] getMuzzleReferences() {
    return new Reference[0];
  }

  /**
   * Returns a list of instrumentation helper classes, automatically detected by muzzle during
   * compilation. Those helpers will be injected into the application classloader.
   *
   * <p>The actual implementation of this method is generated automatically during compilation by
   * the {@code io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * <p><b>This method is generated automatically</b>: if you override it, the muzzle compile plugin
   * will not generate a new implementation, it will leave the existing one.
   */
  protected String[] getMuzzleHelperClassNames() {
    return EMPTY;
  }

  /**
   * Returns a map of {@code class-name to context-class-name}. Keys (and their subclasses) will be
   * associated with a context class stored in the value.
   *
   * <p>The actual implementation of this method is generated automatically during compilation by
   * the {@code io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin}
   * ByteBuddy plugin.
   *
   * <p><b>This method is generated automatically</b>: if you override it, the muzzle compile plugin
   * will not generate a new implementation, it will leave the existing one.
   */
  protected Map<String, String> getMuzzleContextStoreClasses() {
    return Collections.emptyMap();
  }

  /**
   * Instrumentation modules can override this method to provide additional helper classes that are
   * not located in instrumentation packages described in the {@code InstrumentationClassPredicate}
   * class and {@link #isHelperClass(String)} (and not automatically detected by muzzle). These
   * additional classes will be injected into the application classloader first.
   *
   * <p>Implementing {@link #isHelperClass(String)} is generally simpler and less error-prone
   * compared to implementing this method.
   *
   * @deprecated Use {@link #isHelperClass(String)} instead.
   */
  @Deprecated
  protected String[] additionalHelperClassNames() {
    return EMPTY;
  }

  /**
   * Same as {@link #order()}.
   *
   * @deprecated use {@link #order()} instead.
   */
  @Deprecated
  public int getOrder() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  public int order() {
    return getOrder();
  }

  /** Returns resource names to inject into the user's classloader. */
  public String[] helperResourceNames() {
    return EMPTY;
  }

  /**
   * An instrumentation module can implement this method to make sure that the classloader contains
   * the particular library version. It is useful to implement that if the muzzle check does not
   * fail for versions out of the instrumentation's scope.
   *
   * <p>E.g. supposing version 1.0 has class {@code A}, but it was removed in version 2.0; A is not
   * used in the helper classes at all; this module is instrumenting 2.0: this method will return
   * {@code not(hasClassesNamed("A"))}.
   *
   * @return A type matcher used to match the classloader under transform
   */
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return any();
  }

  /** Returns a list of all individual type instrumentation in this module. */
  public abstract List<TypeInstrumentation> typeInstrumentations();

  /**
   * Allows instrumentation modules to disable themselves by default, or to additionally disable
   * themselves on some other condition.
   */
  protected boolean defaultEnabled() {
    return DEFAULT_ENABLED;
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.bootstrap.AgentInitializer.isJavaBefore9;
import static io.opentelemetry.javaagent.extension.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.javaagent.tooling.Utils.getResourceName;
import static io.opentelemetry.javaagent.tooling.matcher.GlobalIgnoresMatcher.globalIgnoresMatcher;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.spi.AgentExtension;
import io.opentelemetry.javaagent.instrumentation.api.SafeServiceLoader;
import io.opentelemetry.javaagent.instrumentation.api.internal.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.spi.BootstrapPackagesProvider;
import io.opentelemetry.javaagent.spi.ComponentInstaller;
import io.opentelemetry.javaagent.spi.IgnoreMatcherProvider;
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.matcher.GlobalClassloaderIgnoresMatcher;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstaller {

  private static final Logger log;

  private static final String JAVAAGENT_ENABLED_CONFIG = "otel.javaagent.enabled";
  private static final String EXCLUDED_CLASSES_CONFIG = "otel.javaagent.exclude-classes";

  // This property may be set to force synchronous ComponentInstaller#afterByteBuddyAgent()
  // execution: the condition for delaying the ComponentInstaller initialization is pretty broad
  // and in case it covers too much javaagent users can file a bug, force sync execution by setting
  // this property to true and continue using the javaagent
  private static final String FORCE_SYNCHRONOUS_COMPONENT_INSTALLER_CONFIG =
      "otel.javaagent.experimental.force-synchronous-component-installers";

  // We set this system property when running the agent with unit tests to allow verifying that we
  // don't ignore libraries that we actually attempt to instrument. It means either the list is
  // wrong or a type matcher is.
  private static final String ADDITIONAL_LIBRARY_IGNORES_ENABLED =
      "otel.javaagent.testing.additional-library-ignores.enabled";

  private static final Map<String, List<Runnable>> CLASS_LOAD_CALLBACKS = new HashMap<>();
  private static volatile Instrumentation INSTRUMENTATION;

  public static Instrumentation getInstrumentation() {
    return INSTRUMENTATION;
  }

  static {
    LoggingConfigurer.configureLogger();
    log = LoggerFactory.getLogger(AgentInstaller.class);

    addByteBuddyRawSetting();
    BootstrapPackagePrefixesHolder.setBoostrapPackagePrefixes(loadBootstrapPackagePrefixes());
    // this needs to be done as early as possible - before the first Config.get() call
    ConfigInitializer.initialize();
    // ensure java.lang.reflect.Proxy is loaded, as transformation code uses it internally
    // loading java.lang.reflect.Proxy after the bytebuddy transformer is set up causes
    // the internal-proxy instrumentation module to transform it, and then the bytebuddy
    // transformation code also tries to load it, which leads to a ClassCircularityError
    // loading java.lang.reflect.Proxy early here still allows it to be retransformed by the
    // internal-proxy instrumentation module after the bytebuddy transformer is set up
    Proxy.class.getName();
  }

  public static void installBytebuddyAgent(Instrumentation inst) {
    logVersionInfo();
    Config config = Config.get();
    if (config.getBooleanProperty(JAVAAGENT_ENABLED_CONFIG, true)) {
      Iterable<ComponentInstaller> componentInstallers = loadComponentProviders();
      installBytebuddyAgent(inst, componentInstallers);
    } else {
      log.debug("Tracing is disabled, not installing instrumentations.");
    }
  }

  /**
   * Install the core bytebuddy agent along with all implementations of {@link
   * InstrumentationModule}.
   *
   * @param inst Java Instrumentation used to install bytebuddy
   * @return the agent's class transformer
   */
  public static ResettableClassFileTransformer installBytebuddyAgent(
      Instrumentation inst, Iterable<ComponentInstaller> componentInstallers) {

    Config config = Config.get();
    installComponentsBeforeByteBuddy(componentInstallers, config);

    INSTRUMENTATION = inst;

    FieldBackedProvider.resetContextMatchers();

    IgnoreMatcherProvider ignoreMatcherProvider = loadIgnoreMatcherProvider();
    log.debug(
        "Ignore matcher provider {} will be used", ignoreMatcherProvider.getClass().getName());

    AgentBuilder.Ignored ignoredAgentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new RedefinitionDiscoveryStrategy())
            .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
            .with(AgentTooling.poolStrategy())
            .with(new ClassLoadListener())
            .with(AgentTooling.locationStrategy())
            // FIXME: we cannot enable it yet due to BB/JVM bug, see
            // https://github.com/raphw/byte-buddy/issues/558
            // .with(AgentBuilder.LambdaInstrumentationStrategy.ENABLED)
            .ignore(any(), GlobalClassloaderIgnoresMatcher.skipClassLoader(ignoreMatcherProvider));

    ignoredAgentBuilder =
        ignoredAgentBuilder.or(
            globalIgnoresMatcher(
                config.getBooleanProperty(ADDITIONAL_LIBRARY_IGNORES_ENABLED, true),
                ignoreMatcherProvider));

    ignoredAgentBuilder = ignoredAgentBuilder.or(matchesConfiguredExcludes());

    AgentBuilder agentBuilder = ignoredAgentBuilder;
    if (log.isDebugEnabled()) {
      agentBuilder =
          agentBuilder
              .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
              .with(new RedefinitionDiscoveryStrategy())
              .with(new RedefinitionLoggingListener())
              .with(new TransformLoggingListener());
    }

    int numberOfLoadedExtensions = 0;
    for (AgentExtension agentExtension : loadAgentExtensions()) {
      log.debug(
          "Loading extension {} [class {}]",
          agentExtension.extensionName(),
          agentExtension.getClass().getName());
      try {
        agentBuilder = agentExtension.extend(agentBuilder);
        numberOfLoadedExtensions++;
      } catch (Exception | LinkageError e) {
        log.error(
            "Unable to load extension {} [class {}]",
            agentExtension.extensionName(),
            agentExtension.getClass().getName(),
            e);
      }
    }
    log.debug("Installed {} extension(s)", numberOfLoadedExtensions);

    ResettableClassFileTransformer resettableClassFileTransformer = agentBuilder.installOn(inst);
    installComponentsAfterByteBuddy(componentInstallers, config);
    return resettableClassFileTransformer;
  }

  private static void installComponentsBeforeByteBuddy(
      Iterable<ComponentInstaller> componentInstallers, Config config) {
    for (ComponentInstaller componentInstaller : componentInstallers) {
      componentInstaller.beforeByteBuddyAgent(config);
    }
  }

  private static void installComponentsAfterByteBuddy(
      Iterable<ComponentInstaller> componentInstallers, Config config) {
    // java.util.logging.LogManager maintains a final static LogManager, which is created during
    // class initialization. Some ComponentInstaller implementations may use JRE bootstrap classes
    // which touch this class (e.g. JFR classes or some MBeans).
    // It is worth noting that starting from Java 9 (JEP 264) Java platform classes no longer use
    // JUL directly, but instead they use a new System.Logger interface, so the LogManager issue
    // applies mainly to Java 8.
    // This means applications which require a custom LogManager may not have a chance to set the
    // global LogManager if one of those ComponentInstallers runs first: it will incorrectly
    // set the global LogManager to the default JVM one in cases where the instrumented application
    // sets the LogManager system property or when the custom LogManager class is not on the system
    // classpath.
    // Our solution is to delay the initialization of ComponentInstallers when we detect a custom
    // log manager being used.
    // Once we see the LogManager class loading, it's safe to run
    // ComponentInstaller#afterByteBuddyAgent() because the application is already setting the
    // global LogManager and ComponentInstaller won't be able to touch it due to classloader
    // locking.
    boolean shouldForceSynchronousComponentInstallerCalls =
        Config.get().getBooleanProperty(FORCE_SYNCHRONOUS_COMPONENT_INSTALLER_CONFIG, false);
    if (!shouldForceSynchronousComponentInstallerCalls
        && isJavaBefore9()
        && isAppUsingCustomLogManager()) {
      log.debug(
          "Custom JUL LogManager detected: delaying ComponentInstaller#afterByteBuddyAgent() calls");
      registerClassLoadCallback(
          "java.util.logging.LogManager",
          new InstallComponentAfterByteBuddyCallback(config, componentInstallers));
    } else {
      for (ComponentInstaller componentInstaller : componentInstallers) {
        componentInstaller.afterByteBuddyAgent(config);
      }
    }
  }

  private static Iterable<ComponentInstaller> loadComponentProviders() {
    return ServiceLoader.load(ComponentInstaller.class);
  }

  private static IgnoreMatcherProvider loadIgnoreMatcherProvider() {
    ServiceLoader<IgnoreMatcherProvider> ignoreMatcherProviders =
        ServiceLoader.load(IgnoreMatcherProvider.class);

    Iterator<IgnoreMatcherProvider> iterator = ignoreMatcherProviders.iterator();
    if (iterator.hasNext()) {
      return iterator.next();
    }
    return new NoopIgnoreMatcherProvider();
  }

  private static List<AgentExtension> loadAgentExtensions() {
    return SafeServiceLoader.load(AgentExtension.class).stream()
        .sorted(Comparator.comparingInt(AgentExtension::order))
        .collect(Collectors.toList());
  }

  private static void addByteBuddyRawSetting() {
    String savedPropertyValue = System.getProperty(TypeDefinition.RAW_TYPES_PROPERTY);
    try {
      System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, "true");
      boolean rawTypes = TypeDescription.AbstractBase.RAW_TYPES;
      if (!rawTypes) {
        log.debug("Too late to enable {}", TypeDefinition.RAW_TYPES_PROPERTY);
      }
    } finally {
      if (savedPropertyValue == null) {
        System.clearProperty(TypeDefinition.RAW_TYPES_PROPERTY);
      } else {
        System.setProperty(TypeDefinition.RAW_TYPES_PROPERTY, savedPropertyValue);
      }
    }
  }

  private static ElementMatcher.Junction<Object> matchesConfiguredExcludes() {
    List<String> excludedClasses = Config.get().getListProperty(EXCLUDED_CLASSES_CONFIG);
    ElementMatcher.Junction matcher = none();
    List<String> literals = new ArrayList<>();
    List<String> prefixes = new ArrayList<>();
    // first accumulate by operation because a lot of work can be aggregated
    for (String excludedClass : excludedClasses) {
      excludedClass = excludedClass.trim();
      if (excludedClass.endsWith("*")) {
        // remove the trailing *
        prefixes.add(excludedClass.substring(0, excludedClass.length() - 1));
      } else {
        literals.add(excludedClass);
      }
    }
    if (!literals.isEmpty()) {
      matcher = matcher.or(namedOneOf(literals));
    }
    for (String prefix : prefixes) {
      // TODO - with a prefix tree this matching logic can be handled by a
      // single longest common prefix query
      matcher = matcher.or(nameStartsWith(prefix));
    }
    return matcher;
  }

  private static List<String> loadBootstrapPackagePrefixes() {
    List<String> bootstrapPackages = new ArrayList<>(Constants.BOOTSTRAP_PACKAGE_PREFIXES);
    Iterable<BootstrapPackagesProvider> bootstrapPackagesProviders =
        SafeServiceLoader.load(BootstrapPackagesProvider.class);
    for (BootstrapPackagesProvider provider : bootstrapPackagesProviders) {
      List<String> packagePrefixes = provider.getPackagePrefixes();
      log.debug(
          "Loaded bootstrap package prefixes from {}: {}",
          provider.getClass().getName(),
          packagePrefixes);
      bootstrapPackages.addAll(packagePrefixes);
    }
    return bootstrapPackages;
  }

  static class RedefinitionLoggingListener implements AgentBuilder.RedefinitionStrategy.Listener {

    private static final Logger log = LoggerFactory.getLogger(RedefinitionLoggingListener.class);

    @Override
    public void onBatch(int index, List<Class<?>> batch, List<Class<?>> types) {}

    @Override
    public Iterable<? extends List<Class<?>>> onError(
        int index, List<Class<?>> batch, Throwable throwable, List<Class<?>> types) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Exception while retransforming " + batch.size() + " classes: " + batch, throwable);
      }
      return Collections.emptyList();
    }

    @Override
    public void onComplete(
        int amount, List<Class<?>> types, Map<List<Class<?>>, Throwable> failures) {}
  }

  static class TransformLoggingListener implements AgentBuilder.Listener {

    private static final TransformSafeLogger log =
        TransformSafeLogger.getLogger(TransformLoggingListener.class);

    @Override
    public void onError(
        String typeName,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        Throwable throwable) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Failed to handle {} for transformation on classloader {}",
            typeName,
            classLoader,
            throwable);
      }
    }

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded,
        DynamicType dynamicType) {
      log.debug("Transformed {} -- {}", typeDescription.getName(), classLoader);
    }

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        boolean loaded) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}

    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {}
  }

  /**
   * Register a callback to run when a class is loading.
   *
   * <p>Caveats:
   *
   * <ul>
   *   <li>This callback will be invoked by a jvm class transformer.
   *   <li>Classes filtered out by {@link AgentInstaller}'s skip list will not be matched.
   * </ul>
   *
   * @param className name of the class to match against
   * @param callback runnable to invoke when class name matches
   */
  public static void registerClassLoadCallback(String className, Runnable callback) {
    synchronized (CLASS_LOAD_CALLBACKS) {
      List<Runnable> callbacks =
          CLASS_LOAD_CALLBACKS.computeIfAbsent(className, k -> new ArrayList<>());
      callbacks.add(callback);
    }
  }

  private static class InstallComponentAfterByteBuddyCallback implements Runnable {
    private final Iterable<ComponentInstaller> componentInstallers;
    private final Config config;

    private InstallComponentAfterByteBuddyCallback(
        Config config, Iterable<ComponentInstaller> componentInstallers) {
      this.componentInstallers = componentInstallers;
      this.config = config;
    }

    @Override
    public void run() {
      /*
       * This callback is called from within bytecode transformer. This can be a problem if callback tries
       * to load classes being transformed. To avoid this we start a thread here that calls the callback.
       * This seems to resolve this problem.
       */
      Thread thread = new Thread(this::runComponentInstallers);
      thread.setName("agent-component-installers");
      thread.setDaemon(true);
      thread.start();
    }

    private void runComponentInstallers() {
      for (ComponentInstaller componentInstaller : componentInstallers) {
        try {
          componentInstaller.afterByteBuddyAgent(config);
        } catch (Exception e) {
          log.error("Failed to execute {}", componentInstaller.getClass().getName(), e);
        }
      }
    }
  }

  private static class ClassLoadListener implements AgentBuilder.Listener {
    @Override
    public void onDiscovery(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {}

    @Override
    public void onTransformation(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b,
        DynamicType dynamicType) {}

    @Override
    public void onIgnored(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule javaModule,
        boolean b) {}

    @Override
    public void onError(
        String s, ClassLoader classLoader, JavaModule javaModule, boolean b, Throwable throwable) {}

    @Override
    public void onComplete(
        String typeName, ClassLoader classLoader, JavaModule javaModule, boolean b) {
      synchronized (CLASS_LOAD_CALLBACKS) {
        List<Runnable> callbacks = CLASS_LOAD_CALLBACKS.get(typeName);
        if (callbacks != null) {
          for (Runnable callback : callbacks) {
            callback.run();
          }
        }
      }
    }
  }

  private static class RedefinitionDiscoveryStrategy
      implements AgentBuilder.RedefinitionStrategy.DiscoveryStrategy {
    private static final AgentBuilder.RedefinitionStrategy.DiscoveryStrategy delegate =
        AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE;

    @Override
    public Iterable<Iterable<Class<?>>> resolve(Instrumentation instrumentation) {
      // filter out our agent classes and injected helper classes
      return () -> streamOf(delegate.resolve(instrumentation)).map(this::filterClasses).iterator();
    }

    private Iterable<Class<?>> filterClasses(Iterable<Class<?>> classes) {
      return () -> streamOf(classes).filter(c -> !isIgnored(c)).iterator();
    }

    private static <T> Stream<T> streamOf(Iterable<T> iterable) {
      return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static boolean isIgnored(Class<?> c) {
      ClassLoader cl = c.getClassLoader();
      if (cl instanceof AgentClassLoader || cl instanceof ExtensionClassLoader) {
        return true;
      }

      return HelperInjector.isInjectedClass(c);
    }
  }

  /** Detect if the instrumented application is using a custom JUL LogManager. */
  private static boolean isAppUsingCustomLogManager() {
    String jbossHome = System.getenv("JBOSS_HOME");
    if (jbossHome != null) {
      log.debug("Found JBoss: {}; assuming app is using custom LogManager", jbossHome);
      // JBoss/Wildfly is known to set a custom log manager after startup.
      // Originally we were checking for the presence of a jboss class,
      // but it seems some non-jboss applications have jboss classes on the classpath.
      // This would cause ComponentInstaller initialization to be delayed indefinitely.
      // Checking for an environment variable required by jboss instead.
      return true;
    }

    String customLogManager = System.getProperty("java.util.logging.manager");
    if (customLogManager != null) {
      log.debug(
          "Detected custom LogManager configuration: java.util.logging.manager={}",
          customLogManager);
      boolean onSysClasspath =
          ClassLoader.getSystemResource(getResourceName(customLogManager)) != null;
      log.debug(
          "Class {} is on system classpath: {}delaying ComponentInstaller#afterByteBuddyAgent()",
          customLogManager,
          onSysClasspath ? "not " : "");
      // Some applications set java.util.logging.manager but never actually initialize the logger.
      // Check to see if the configured manager is on the system classpath.
      // If so, it should be safe to initialize ComponentInstaller which will setup the log manager:
      // LogManager tries to load the implementation first using system CL, then falls back to
      // current context CL
      return !onSysClasspath;
    }

    return false;
  }

  private static void logVersionInfo() {
    VersionLogger.logAllVersions();
    log.debug(
        AgentInstaller.class.getName() + " loaded on " + AgentInstaller.class.getClassLoader());
  }

  private static class NoopIgnoreMatcherProvider implements IgnoreMatcherProvider {
    @Override
    public Result classloader(ClassLoader classLoader) {
      return Result.DEFAULT;
    }

    @Override
    public Result type(TypeDescription target) {
      return Result.DEFAULT;
    }
  }

  private AgentInstaller() {}
}

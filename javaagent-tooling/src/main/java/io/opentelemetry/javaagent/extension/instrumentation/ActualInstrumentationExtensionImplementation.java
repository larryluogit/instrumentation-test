/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.failSafe;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.AgentExtensionTooling;
import io.opentelemetry.javaagent.extension.log.TransformSafeLogger;
import io.opentelemetry.javaagent.extension.muzzle.Mismatch;
import io.opentelemetry.javaagent.extension.muzzle.ReferenceMatcher;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.javaagent.tooling.context.FieldBackedProvider;
import io.opentelemetry.javaagent.tooling.context.InstrumentationContextProvider;
import io.opentelemetry.javaagent.tooling.context.NoopContextProvider;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(InstrumentationExtensionImplementation.class)
public final class ActualInstrumentationExtensionImplementation
    extends InstrumentationExtensionImplementation {
  private static final TransformSafeLogger log =
      TransformSafeLogger.getLogger(InstrumentationModule.class);
  private static final Logger muzzleLog = LoggerFactory.getLogger("muzzleMatcher");

  // Added here instead of AgentInstaller's ignores because it's relatively
  // expensive. https://github.com/DataDog/dd-trace-java/pull/1045
  public static final ElementMatcher.Junction<AnnotationSource> NOT_DECORATOR_MATCHER =
      not(isAnnotatedWith(named("javax.decorator.Decorator")));

  @Override
  AgentBuilder extend(
      InstrumentationModule instrumentationModule,
      AgentBuilder parentAgentBuilder,
      AgentExtensionTooling tooling) {
    List<String> helperClassNames = instrumentationModule.getAllHelperClassNames();
    List<String> helperResourceNames = asList(instrumentationModule.helperResourceNames());
    List<TypeInstrumentation> typeInstrumentations = instrumentationModule.typeInstrumentations();
    if (typeInstrumentations.isEmpty()) {
      if (!helperClassNames.isEmpty() || !helperResourceNames.isEmpty()) {
        log.warn(
            "Helper classes and resources won't be injected if no types are instrumented: {}",
            instrumentationModule.extensionName());
      }

      return parentAgentBuilder;
    }

    ElementMatcher.Junction<ClassLoader> moduleClassLoaderMatcher =
        instrumentationModule.classLoaderMatcher();
    MuzzleMatcher muzzleMatcher = new MuzzleMatcher(instrumentationModule, tooling);
    AgentBuilder.Transformer helperInjector =
        new HelperInjector(
            instrumentationModule.extensionName(), helperClassNames, helperResourceNames);
    InstrumentationContextProvider contextProvider =
        createInstrumentationContextProvider(instrumentationModule);

    AgentBuilder agentBuilder = parentAgentBuilder;
    for (TypeInstrumentation typeInstrumentation : typeInstrumentations) {
      AgentBuilder.Identified.Extendable extendableAgentBuilder =
          agentBuilder
              .type(
                  failSafe(
                      typeInstrumentation.typeMatcher(),
                      "Instrumentation type matcher unexpected exception: " + getClass().getName()),
                  failSafe(
                      moduleClassLoaderMatcher.and(typeInstrumentation.classLoaderOptimization()),
                      "Instrumentation class loader matcher unexpected exception: "
                          + getClass().getName()))
              .and(NOT_DECORATOR_MATCHER)
              .and(muzzleMatcher)
              .transform(ConstantAdjuster.instance())
              .transform(helperInjector);
      extendableAgentBuilder = contextProvider.instrumentationTransformer(extendableAgentBuilder);
      extendableAgentBuilder =
          applyInstrumentationTransformers(
              typeInstrumentation.transformers(), extendableAgentBuilder, tooling);
      extendableAgentBuilder = contextProvider.additionalInstrumentation(extendableAgentBuilder);

      agentBuilder = extendableAgentBuilder;
    }

    return agentBuilder;
  }

  private InstrumentationContextProvider createInstrumentationContextProvider(
      InstrumentationModule instrumentationModule) {
    Map<String, String> contextStore = instrumentationModule.getMuzzleContextStoreClasses();
    if (!contextStore.isEmpty()) {
      return new FieldBackedProvider(instrumentationModule.getClass(), contextStore);
    } else {
      return NoopContextProvider.INSTANCE;
    }
  }

  private AgentBuilder.Identified.Extendable applyInstrumentationTransformers(
      Map<? extends ElementMatcher<? super MethodDescription>, String> transformers,
      AgentBuilder.Identified.Extendable agentBuilder,
      AgentExtensionTooling tooling) {
    for (Map.Entry<? extends ElementMatcher<? super MethodDescription>, String> entry :
        transformers.entrySet()) {
      agentBuilder =
          agentBuilder.transform(
              new AgentBuilder.Transformer.ForAdvice()
                  .include(
                      tooling.classLoaders().bootstrapProxyClassLoader(),
                      tooling.classLoaders().agentClassLoader())
                  .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                  .advice(entry.getKey(), entry.getValue()));
    }
    return agentBuilder;
  }

  /**
   * A ByteBuddy matcher that decides whether this instrumentation should be applied. Calls
   * generated {@link ReferenceMatcher}: if any mismatch with the passed {@code classLoader} is
   * found this instrumentation is skipped.
   */
  private static class MuzzleMatcher implements AgentBuilder.RawMatcher {
    private final InstrumentationModule instrumentationModule;
    private final AgentExtensionTooling tooling;

    private MuzzleMatcher(
        InstrumentationModule instrumentationModule, AgentExtensionTooling tooling) {
      this.instrumentationModule = instrumentationModule;
      this.tooling = tooling;
    }

    @Override
    public boolean matches(
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain) {
      /* Optimization: calling getMuzzleReferenceMatcher() inside this method
       * prevents unnecessary loading of muzzle references during agentBuilder
       * setup.
       */
      ReferenceMatcher muzzle = instrumentationModule.getMuzzleReferenceMatcher();
      if (muzzle != null) {
        boolean isMatch = muzzle.matches(tooling, classLoader);

        if (!isMatch) {
          if (muzzleLog.isWarnEnabled()) {
            muzzleLog.warn(
                "Instrumentation skipped, mismatched references were found: {} -- {} on {}",
                instrumentationModule.extensionName(),
                instrumentationModule.getClass().getName(),
                classLoader);
            List<Mismatch> mismatches = muzzle.getMismatchedReferenceSources(tooling, classLoader);
            for (Mismatch mismatch : mismatches) {
              muzzleLog.warn("-- {}", mismatch);
            }
          }
        } else {
          if (log.isDebugEnabled()) {
            log.debug(
                "Applying instrumentation: {} -- {} on {}",
                instrumentationModule.extensionName(),
                instrumentationModule.getClass().getName(),
                classLoader);
          }
        }

        return isMatch;
      }
      return true;
    }
  }
}

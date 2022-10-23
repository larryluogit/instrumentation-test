/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.jpms;

import static java.util.logging.Level.FINE;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

public class ExposeRmiModuleInstrumentation implements TypeInstrumentation {
  private static final Logger logger =
      Logger.getLogger(ExposeRmiModuleInstrumentation.class.getName());

  private final AtomicBoolean instrumented = new AtomicBoolean();

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    ElementMatcher.Junction<TypeDescription> notInstrumented =
        new ElementMatcher.Junction.AbstractBase<TypeDescription>() {

          @Override
          public boolean matches(TypeDescription target) {
            return !instrumented.get();
          }
        };

    return notInstrumented.and(nameStartsWith("sun.rmi"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return new ElementMatcher.Junction.AbstractBase<ClassLoader>() {

      @Override
      public boolean matches(ClassLoader target) {
        // runs only in bootstrap class loader
        return JavaModule.isSupported() && target == null;
      }
    };
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) -> {
          if (javaModule != null && javaModule.isNamed()) {
            // using Java8BytecodeBridge because it's in the unnamed module in the bootstrap
            // loader, and that's where the rmi instrumentation helper classes will end up
            JavaModule helperModule = JavaModule.ofType(Java8BytecodeBridge.class);
            // expose sun.rmi.server package to unnamed module
            ClassInjector.UsingInstrumentation.redefineModule(
                InstrumentationHolder.getInstrumentation(),
                javaModule,
                Collections.emptySet(),
                Collections.singletonMap("sun.rmi.server", Collections.singleton(helperModule)),
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptyMap());

            instrumented.set(true);
            logger.log(
                FINE,
                "Exposed package \"sun.rmi.server\" in module {0} to unnamed module",
                javaModule);
          }
          return builder;
        });
  }
}

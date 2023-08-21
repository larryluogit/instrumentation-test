/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Classloader used to load the helper classes from {@link
 * io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule}s, so that those
 * classes have access to both the agent/extension classes and the instrumented application classes.
 *
 * <p>This classloader implements the following classloading delegation strategy:
 *
 * <ul>
 *   <li>First, injected classes are considered (usually the helper classes from the
 *       InstrumentationModule)
 *   <li>Next, the classloader looks in the agent or extension classloader, depending on where the
 *       InstrumentationModule comes from
 *   <li>Finally, the instrumented application classloader is checked for the class
 * </ul>
 *
 * <p>In addition, this classloader ensures that the lookup of corresponding .class resources follow
 * the same delegation strategy, so that bytecode inspection tools work correctly.
 */
class InstrumentationModuleClassLoader extends ClassLoader {

  static {
    ClassLoader.registerAsParallelCapable();
  }

  private static final Map<String, ClassCopySource> ALWAYS_INJECTED_CLASSES =
      Collections.singletonMap(
          LookupExposer.class.getName(), ClassCopySource.create(LookupExposer.class).cached());
  private static final ProtectionDomain PROTECTION_DOMAIN = getProtectionDomain();
  private static final MethodHandle FIND_PACKAGE_METHOD = getFindPackageMethod();

  private final Map<String, ClassCopySource> additionalInjectedClasses;
  private final ClassLoader agentOrExtensionCl;
  private final ClassLoader instrumentedCl;
  private volatile MethodHandles.Lookup cachedLookup;

  public InstrumentationModuleClassLoader(
      ClassLoader instrumentedCl,
      ClassLoader agentOrExtensionCl,
      Map<String, ClassCopySource> injectedClasses) {
    // agent/extension-classloader is "main"-parent, but class lookup is overridden
    super(agentOrExtensionCl);
    additionalInjectedClasses = injectedClasses;
    this.agentOrExtensionCl = agentOrExtensionCl;
    this.instrumentedCl = instrumentedCl;
  }

  /**
   * Provides a Lookup within this classloader. See {@link LookupExposer} for the details.
   *
   * @return a lookup capable of accessing public types in this classloader
   */
  public MethodHandles.Lookup getLookup() {
    if (cachedLookup == null) {
      // Load the injected copy of LookupExposer and invoke it
      try {
        // we don't mind the race condition causing the initialization to run multiple times here
        Class<?> lookupExposer = loadClass(LookupExposer.class.getName());
        cachedLookup = (MethodHandles.Lookup) lookupExposer.getMethod("getLookup").invoke(null);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return cachedLookup;
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> result = findLoadedClass(name);

      // This CL is self-first: Injected class are loaded BEFORE a parent lookup
      if (result == null) {
        ClassCopySource injected = getInjectedClass(name);
        if (injected != null) {
          byte[] bytecode = injected.getBytecode();
          if (System.getSecurityManager() == null) {
            result = defineClassWithPackage(name, bytecode);
          } else {
            result =
                AccessController.doPrivileged(
                    (PrivilegedAction<Class<?>>) () -> defineClassWithPackage(name, bytecode));
          }
        }
      }
      if (result == null) {
        result = tryLoad(agentOrExtensionCl, name);
      }
      if (result == null) {
        result = tryLoad(instrumentedCl, name);
      }

      if (result != null) {
        if (resolve) {
          resolveClass(result);
        }
        return result;
      } else {
        throw new ClassNotFoundException(name);
      }
    }
  }

  private static Class<?> tryLoad(ClassLoader cl, String name) {
    try {
      return cl.loadClass(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  @Override
  public URL getResource(String resourceName) {
    String className = resourceToClassName(resourceName);
    if (className == null) {
      // delegate to just the default parent (the agent classloader)
      return super.getResource(resourceName);
    }
    // for classes use the same precedence as in loadClass
    ClassCopySource injected = getInjectedClass(className);
    if (injected != null) {
      return injected.getUrl();
    }
    URL fromAgentCl = agentOrExtensionCl.getResource(resourceName);
    if (fromAgentCl != null) {
      return fromAgentCl;
    }
    return instrumentedCl.getResource(resourceName);
  }

  @Override
  public Enumeration<URL> getResources(String resourceName) throws IOException {
    String className = resourceToClassName(resourceName);
    if (className == null) {
      return super.getResources(resourceName);
    }
    URL resource = getResource(resourceName);
    List<URL> result =
        resource != null ? Collections.singletonList(resource) : Collections.emptyList();
    return Collections.enumeration(result);
  }

  @Nullable
  private static String resourceToClassName(String resourceName) {
    if (!resourceName.endsWith(".class")) {
      return null;
    }
    String className = resourceName;
    if (className.startsWith("/")) {
      className = className.substring(1);
    }
    className = className.replace('/', '.');
    className = className.substring(0, className.length() - ".class".length());
    return className;
  }

  @Nullable
  private ClassCopySource getInjectedClass(String name) {
    ClassCopySource alwaysInjected = ALWAYS_INJECTED_CLASSES.get(name);
    if (alwaysInjected != null) {
      return alwaysInjected;
    }
    return additionalInjectedClasses.get(name);
  }

  private Class<?> defineClassWithPackage(String name, byte[] bytecode) {
    int lastDotIndex = name.lastIndexOf('.');
    if (lastDotIndex != -1) {
      String packageName = name.substring(0, lastDotIndex);
      safeDefinePackage(packageName);
    }
    return defineClass(name, bytecode, 0, bytecode.length, PROTECTION_DOMAIN);
  }

  private void safeDefinePackage(String packageName) {
    if (findPackage(packageName) == null) {
      try {
        definePackage(packageName, null, null, null, null, null, null, null);
      } catch (IllegalArgumentException e) {
        // Can happen if a parent classloader attempts to define the same package at the same time
        // in Java 8
        if (findPackage(packageName) == null) {
          // package still doesn't exist, the IllegalArgumentException must be for a different
          // reason than a race condition
          throw e;
        }
      }
    }
  }

  /**
   * Invokes {@link #getPackage(String)} for Java 8 and {@link #getDefinedPackage(String)} for Java
   * 9+.
   *
   * <p>Package-private for testing.
   *
   * @param name the name of the package find
   * @return the found package or null if it was not found.
   */
  @SuppressWarnings({"deprecation", "InvalidLink"})
  Package findPackage(String name) {
    try {
      return (Package) FIND_PACKAGE_METHOD.invokeExact((ClassLoader) this, name);
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static ProtectionDomain getProtectionDomain() {
    if (System.getSecurityManager() == null) {
      return InstrumentationModuleClassLoader.class.getProtectionDomain();
    }
    return AccessController.doPrivileged(
        (PrivilegedAction<ProtectionDomain>)
            ((Class<?>) InstrumentationModuleClassLoader.class)::getProtectionDomain);
  }

  private static MethodHandle getFindPackageMethod() {
    MethodType methodType = MethodType.methodType(Package.class, String.class);
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      try {
        return lookup.findVirtual(ClassLoader.class, "getDefinedPackage", methodType);
      } catch (NoSuchMethodException e) {
        // Java 8 case
        try {
          return lookup.findVirtual(ClassLoader.class, "getPackage", methodType);
        } catch (NoSuchMethodException ex) {
          throw new IllegalStateException("expected method to always exist!", ex);
        }
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Method should be accessible from here", e);
    }
  }
}

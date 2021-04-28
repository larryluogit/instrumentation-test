/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.ShadingRemapper.rule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;

/**
 * This classloader is used to load arbitrary extensions for Otel Java instrumentation agent. Such
 * extensions may include SDK components (exporters or propagators) and additional instrumentations.
 * They have to be isolated and shaded to reduce interference with the user application and to make
 * it compatible with shaded SDK used by the agent.
 */
// TODO find a way to initialize logging before using this class
public class ExtensionClassLoader extends URLClassLoader {

  // We need to prefix the names to prevent the gradle shadowJar relocation rules from touching
  // them. It's possible to do this by excluding this class from shading, but it may cause issue
  // with transitive dependencies down the line.
  private static final ShadingRemapper remapper =
      new ShadingRemapper(
          rule("#io.opentelemetry.api", "#io.opentelemetry.javaagent.shaded.io.opentelemetry.api"),
          rule(
              "#io.opentelemetry.context",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.context"),
          rule(
              "#io.opentelemetry.extension.aws",
              "#io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws"),
          rule("#java.util.logging.Logger", "#io.opentelemetry.javaagent.bootstrap.PatchLogger"),
          rule("#org.slf4j", "#io.opentelemetry.javaagent.slf4j"));

//  private final Manifest manifest;

  public static ClassLoader getInstance(ClassLoader parent) {
    // TODO add support for old deprecated properties, otel.exporter.jar and otel.initializer.jar
    // TODO add support for system properties
    URL extension = parseLocation(System.getenv("OTEL_JAVAAGENT_EXTENSIONS"));
    if (extension != null) {
      try {
//        URL wrappedUrl = new URL(extension, extension.getFile(), new RemappingURLStreamHandler());
        URL wrappedUrl = new URL("otel", null, -1, "/", new RemappingURLStreamHandler(extension));
        return new ExtensionClassLoader(wrappedUrl, parent);
      } catch (MalformedURLException e) {
        // This can't happen with current URL constructor
        throw new IllegalStateException("URL malformed.  Unsupported JDK?", e);
      }
    }
    return parent;
  }

  private static URL parseLocation(String name) {
    if (name == null) {
      return null;
    }
    try {
      return new File(name).toURI().toURL();
    } catch (MalformedURLException e) {
      System.err.println("Filename could not be parsed: %s. Extension location is ignored");
      e.printStackTrace();
    }
    return null;
  }

  public ExtensionClassLoader(URL url, ClassLoader parent) {
    super(new URL[] {url}, parent);
//    this.manifest = getManifest(url);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    System.out.println("FindClass " + name);
    Class<?> result = super.findClass(name);
    System.out.println("Found class " + result + " from " + result.getClassLoader());
    return result;
  }
//    // Use resource loading to get the class as a stream of bytes, then use ASM to transform it.
//    InputStream in = super.getResourceAsStream(name.replace('.', '/') + ".class");
//    if (in == null) {
//      throw new ClassNotFoundException(name);
//    }
//    try {
//      byte[] bytes = remapClassBytes(in);
//      definePackageIfNeeded(name);
//      return defineClass(name, bytes, 0, bytes.length);
//    } catch (IOException e) {
//      throw new ClassNotFoundException(name, e);
//    } finally {
//      try {
//        in.close();
//      } catch (IOException e) {
//        e.printStackTrace();
//        //        log.debug(e.getMessage(), e);
//      }
//    }
//  }

  @Override
  public URL findResource(String name) {
    System.out.println("Searching for " + name);
    URL url = super.findResource(name);
    System.out.println("Found resource " + url);
    return url;
//    if (!name.endsWith(".class")) {
//    }

//    try {
//      URLConnection urlc = url.openConnection();
//      InputStream is = urlc.getInputStream();
//      byte[] remappedClass = remapClassBytes(is);
//      return new URL("otel",
//          URLEncoder.encode(name.replace('.', '/'), "UTF-8"),
//          -1,
//          "",
//          new ByteArrayUrlStreamHandler(remappedClass));
//    } catch (IOException e) {
//      e.printStackTrace();
//      return null;
//    }
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    System.out.println("getResourceAsStream for " + name);
    InputStream originalStream = super.getResourceAsStream(name);
//    if (name.endsWith(".class")) {
//      try {
//        byte[] remappedClass = remapClassBytes(originalStream);
//        return new ByteArrayInputStream(remappedClass);
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
//    }

    return originalStream;
  }

//  private void definePackageIfNeeded(String className) {
//    String packageName = getPackageName(className);
//    if (packageName == null) {
//      // default package
//      return;
//    }
//    if (isPackageDefined(packageName)) {
//      // package has already been defined
//      return;
//    }
//    try {
//      definePackage(packageName);
//    } catch (IllegalArgumentException e) {
//      // this exception is thrown when the package has already been defined, which is possible due
//      // to race condition with the check above
//      if (!isPackageDefined(packageName)) {
//        // this shouldn't happen however
//        e.printStackTrace();
//        //        log.error(e.getMessage(), e);
//      }
//    }
//  }

  private boolean isPackageDefined(String packageName) {
    return getPackage(packageName) != null;
  }

//  private void definePackage(String packageName) {
//    if (manifest == null) {
//      definePackage(packageName, null, null, null, null, null, null, null);
//    } else {
//      definePackage(packageName, manifest, null);
//    }
//  }

  private static byte[] remapClassBytes(InputStream in) throws IOException {
    ClassWriter cw = new ClassWriter(0);
    ClassReader cr = new ClassReader(in);
    cr.accept(new ClassRemapper(cw, remapper), ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  private static String getPackageName(String className) {
    int index = className.lastIndexOf('.');
    return index == -1 ? null : className.substring(0, index);
  }

  private static Manifest getManifest(URL url) {
    try (JarFile jarFile = new JarFile(url.toURI().getPath())) {
      return jarFile.getManifest();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
      //      log.warn(e.getMessage(), e);
    }
    return null;
  }
}

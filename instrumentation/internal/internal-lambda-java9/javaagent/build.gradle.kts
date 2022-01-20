plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // Fails with no error message :crying_cat_face:
  minJavaVersionSupported.set(JavaVersion.VERSION_1_9)
  // Works:
  // minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

tasks {
  compileJava {
    with(options) {
      // Because this module targets Java 9, we trigger this compiler bug which was fixed but not
      // backported to Java 9 compilation.
      // https://bugs.openjdk.java.net/browse/JDK-8209058
      compilerArgs.add("-Xlint:none")
    }
  }
}

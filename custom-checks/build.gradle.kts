plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("com.google.errorprone:error_prone_core")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testImplementation("com.google.errorprone:error_prone_test_helpers")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

// We cannot use "--release" javac option here because that will forbid exporting com.sun.tools package.
// We also can't seem to use the toolchain without the "--release" option. So disable everything.

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
  toolchain {
    languageVersion.set(null as JavaLanguageVersion?)
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(null as Int?)

      compilerArgs.addAll(
        listOf(
          "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
          "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
      )
    }
  }
}

// Our conventions apply this project as a dependency in the errorprone configuration, which would cause
// a circular dependency if trying to compile this project with that still there. So we filter this
// project out.
configurations {
  named("errorprone") {
    dependencies.removeIf {
      it is ProjectDependency && it.dependencyProject == project
    }
  }
}

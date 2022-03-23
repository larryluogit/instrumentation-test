import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("org.xbib.gradle.plugin.jflex")

  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
  id("otel.jmh-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-semconv")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    exclude("**/concurrentlinkedhashmap/**")
  }

  // Work around https://github.com/jflex-de/jflex/issues/762
  compileJava {
    with(options) {
      compilerArgs.add("-Xlint:-fallthrough")
    }
  }

  sourcesJar {
    dependsOn("generateJflex")
  }

  val testStatementSanitizerConfig by registering(Test::class) {
    filter {
      includeTestsMatching("StatementSanitizationConfigTest")
    }
    include("**/StatementSanitizationConfigTest.*")
    jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=false")
  }

  test {
    filter {
      excludeTestsMatching("StatementSanitizationConfigTest")
    }
  }

  check {
    dependsOn(testStatementSanitizerConfig)
  }

  // TODO this should live in jmh-conventions
  named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.errorprone {
      isEnabled.set(false)
    }
  }
}

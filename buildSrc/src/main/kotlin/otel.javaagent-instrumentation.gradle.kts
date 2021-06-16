import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.opentelemetry.instrumentation.gradle.bytebuddy.ByteBuddyPluginConfigurator

plugins {
  id("net.bytebuddy.byte-buddy")

  id("otel.java-conventions")
  id("otel.shadow-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"
// Shadow is only for testing, not publishing.
extra["noShadowPublish"] = true

val skipPublish: Boolean? by extra

if (skipPublish != true) {
  apply(plugin = "otel.publish-conventions")
}

apply(plugin = "otel.instrumentation-conventions")

if (projectDir.name == "javaagent") {
  apply(plugin = "muzzle")

  base.archivesBaseName = projectDir.parentFile.name
}

val toolingRuntime by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val bootstrapRuntime by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":javaagent-api"))
  compileOnly(project(":javaagent-bootstrap"))
  // Apply common dependencies for instrumentation.
  compileOnly(project(":javaagent-extension-api")) {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
  }
  compileOnly(project(":javaagent-tooling")) {
    // OpenTelemetry SDK is not needed for compilation
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk")
    exclude(group = "io.opentelemetry", module = "opentelemetry-sdk-metrics")
  }
  compileOnly("net.bytebuddy:byte-buddy")
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")
  compileOnly("org.slf4j:slf4j-api")

  testImplementation("io.opentelemetry:opentelemetry-api")

  testImplementation(project(":testing-common"))
  testAnnotationProcessor("net.bytebuddy:byte-buddy")
  testCompileOnly("net.bytebuddy:byte-buddy")

  testImplementation("org.testcontainers:testcontainers")

  toolingRuntime(project(path = ":javaagent-tooling", configuration = "instrumentationMuzzle"))
  toolingRuntime(project(path = ":javaagent-extension-api", configuration = "instrumentationMuzzle"))

  bootstrapRuntime(project(path = ":javaagent-bootstrap", configuration = "instrumentationMuzzle"))
}

afterEvaluate {
  val pluginName = "io.opentelemetry.javaagent.tooling.muzzle.collector.MuzzleCodeGenerationPlugin"
  ByteBuddyPluginConfigurator(project, sourceSets.main.get(), pluginName,
    toolingRuntime.plus(configurations.runtimeClasspath.get()))
    .configure()
}

val testInstrumentation by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

tasks.named<ShadowJar>("shadowJar").configure {
  configurations = listOf(project.configurations.runtimeClasspath.get(), testInstrumentation)

  archiveFileName.set("agent-testing.jar")
}

evaluationDependsOn(":testing:agent-for-testing")

// need to run this after evaluate because testSets plugin adds new test tasks
afterEvaluate {
  tasks.withType<Test>().configureEach {
    val shadowJar = tasks.shadowJar.get()
    val agentShadowJar = project(":testing:agent-for-testing").tasks.shadowJar.get()

    inputs.file(shadowJar.archiveFile)

    dependsOn(shadowJar)
    dependsOn(agentShadowJar)

    jvmArgs("-Dotel.javaagent.debug=true")
    jvmArgs("-javaagent:${agentShadowJar.archiveFile.get().asFile.absolutePath}")
    jvmArgs("-Dotel.javaagent.experimental.initializer.jar=${shadowJar.archiveFile.get().asFile.absolutePath}")
    jvmArgs("-Dotel.javaagent.testing.additional-library-ignores.enabled=false")
    jvmArgs("-Dotel.javaagent.testing.fail-on-context-leak=true")
    // prevent sporadic gradle deadlocks, see SafeLogger for more details
    jvmArgs("-Dotel.javaagent.testing.transform-safe-logging.enabled=true")

    // We do fine-grained filtering of the classpath of this codebase's sources since Gradle's
    // configurations will include transitive dependencies as well, which tests do often need.
    classpath = classpath.filter {
      // The sources are packaged into the testing jar so we need to make sure to exclude from the test
      // classpath, which automatically inherits them, to ensure our shaded versions are used.
      if (file("${buildDir}/resources/main").equals(it) || file("${buildDir}/classes/java/main").equals(it)) {
        return@filter false
      }
      // If agent depends on some shared instrumentation module that is not a testing module, it will
      // be packaged into the testing jar so we need to make sure to exclude from the test classpath.
      val libPath = it.absolutePath
      val instrumentationPath = file("${rootDir}/instrumentation/").absolutePath
      if (libPath.startsWith(instrumentationPath) &&
        libPath.endsWith(".jar") &&
        !libPath.substring(instrumentationPath.length).contains("testing")) {
        return@filter false
      }
      return@filter true
    }
  }
}

configurations.configureEach {
  if (name.toLowerCase().endsWith("testruntimeclasspath")) {
    // Added by agent, don't let Gradle bring it in when running tests.
    exclude(module = "javaagent-bootstrap")
  }
}

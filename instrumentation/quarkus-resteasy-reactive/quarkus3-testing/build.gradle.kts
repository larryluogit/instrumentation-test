plugins {
  id("otel.javaagent-testing")

  id("io.quarkus") version "3.0.0.Final"
}

// io.quarkus.platform:quarkus-bom is missing for 3.0.0.Final
var quarkusVersion = "3.0.1.Final"
if (findProperty("testLatestDeps") as Boolean) {
  quarkusVersion = "+"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
  implementation("io.quarkus:quarkus-resteasy-reactive")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:quarkus-resteasy-reactive:javaagent"))

  testImplementation(project(":instrumentation:quarkus-resteasy-reactive:common-testing"))
  testImplementation("io.quarkus:quarkus-junit5")
}

tasks.named("compileJava").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}
tasks.named("sourcesJar").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}

plugins {
  id("otel.javaagent-testing")

  id("io.quarkus") version "2.16.7.Final"
}

dependencies {
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:2.16.7.Final"))
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
tasks.named("checkstyleTest").configure {
  dependsOn(tasks.named("compileQuarkusGeneratedSourcesJava"))
}
tasks.named("compileTestJava").configure {
  dependsOn(tasks.named("compileQuarkusTestGeneratedSourcesJava"))
}

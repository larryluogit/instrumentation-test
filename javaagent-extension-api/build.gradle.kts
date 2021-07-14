plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")
  api("net.bytebuddy:byte-buddy")

  implementation(project(":instrumentation-api"))
  implementation(project(":javaagent-instrumentation-api"))
  implementation("org.slf4j:slf4j-api")

  // metrics are unstable, do not expose as api
  implementation("io.opentelemetry:opentelemetry-sdk-metrics")
}

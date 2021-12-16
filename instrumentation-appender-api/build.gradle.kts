plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  implementation("io.opentelemetry:opentelemetry-sdk-logs")

  testImplementation("org.assertj:assertj-core")
}

plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation-appender-api-internal"))
  implementation(project(":instrumentation-appender-sdk-internal"))

  library("ch.qos.logback:logback-classic:0.9.16")

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental log attributes
  jvmArgs("-Dotel.instrumentation.logback-appender.experimental.capture-mdc-attributes=*")
}

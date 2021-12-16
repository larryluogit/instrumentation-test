plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-logs")

  library("org.apache.logging.log4j:log4j-core:2.16.0")

  testImplementation("io.opentelemetry:opentelemetry-sdk-logs")

  testImplementation("org.mockito:mockito-core")
}

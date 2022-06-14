plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.mchange:c3p0:0.9.2")

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
}

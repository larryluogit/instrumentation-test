plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  // this instrumentation needs to be able to reference both the OpenTelemetry API
  // that is shaded in the bootstrap class loader (for sending telemetry to the agent),
  // and the OpenTelemetry API that the user brings (in order to capture that telemetry)
  //
  // since (all) instrumentation already uses OpenTelemetry API for sending telemetry to the agent,
  // this instrumentation uses a "temporarily shaded" OpenTelemetry API to represent the
  // OpenTelemetry API that the user brings
  //
  // then later, after the OpenTelemetry API in the bootstrap class loader is shaded,
  // the "temporarily shaded" OpenTelemetry API is unshaded, so that it will apply to the
  // OpenTelemetry API that the user brings
  //
  // so in the code "application.io.opentelemetry.*" refers to the (unshaded) OpenTelemetry API that
  // the application brings (as those references will be translated during the build to remove the
  // "application." prefix)
  //
  // and in the code "io.opentelemetry.*" refers to the (shaded) OpenTelemetry API that is used by
  // the agent (as those references will later be shaded)
  compileOnly("io.opentelemetry:opentelemetry-api")
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
}

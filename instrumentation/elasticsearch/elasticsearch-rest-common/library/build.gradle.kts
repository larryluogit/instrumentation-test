plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.elasticsearch.client:rest:5.0.0")
}

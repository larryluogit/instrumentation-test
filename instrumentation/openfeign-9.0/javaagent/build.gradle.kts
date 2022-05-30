plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.github.openfeign")
    module.set("feign-core")
    versions.set("[9.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.github.openfeign:feign-core:9.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

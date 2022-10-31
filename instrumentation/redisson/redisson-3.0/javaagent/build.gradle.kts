plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.0.0,3.17.0)")
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  implementation(project(":instrumentation:redisson:redisson-common:javaagent"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:redisson:redisson-3.17:javaagent"))

  testImplementation(project(":instrumentation:redisson:redisson-common:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.16.+") // see redisson-3.17 module
}

tasks.test {
  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
}

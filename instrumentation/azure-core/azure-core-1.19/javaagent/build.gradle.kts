plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.19.0,1.36.0)")
    assertInverse.set(true)
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.19:library-instrumentation-shaded", configuration = "shadow"))

  library("com.azure:azure-core:1.19.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.14:javaagent"))
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.36:javaagent"))

  latestDepTestLibrary("com.azure:azure-core:1.35.0")
}

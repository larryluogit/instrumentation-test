plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("com.squareup.okhttp3:okhttp:3.0.0")

  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
}

tasks {
  val testStableSemconv by registering(Test::class) {
    filter {
      includeTestsMatching("*StableSemconvTest")
    }
    include("**/*StableSemconvTest.*")
    jvmArgs("-Dotel.semconv-stability.opt-in=http")
  }

  test {
    filter {
      excludeTestsMatching("*StableSemconvTest")
    }
  }

  check {
    dependsOn(testStableSemconv)
  }
}

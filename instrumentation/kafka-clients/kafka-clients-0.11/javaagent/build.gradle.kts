plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-clients")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:javaagent"))

  library("org.apache.kafka:kafka-clients:0.11.0.0")

  testImplementation("org.testcontainers:kafka")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testPropagationDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("KafkaClientPropagationDisabledTest")
      isFailOnNoMatchingTests = false
    }
    include("**/KafkaClientPropagationDisabledTest.*")
    jvmArgs("-Dotel.instrumentation.kafka.client-propagation.enabled=false")
  }

  named<Test>("test") {
    dependsOn(testPropagationDisabled)
    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      isFailOnNoMatchingTests = false
    }
  }
}

plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-streams")
    versions.set("[0.11.0.0,3)")
  }
}

val versions: Map<String, String> by project

dependencies {
  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:javaagent"))

  library("org.apache.kafka:kafka-streams:0.11.0.0")

  // Include kafka-clients instrumentation for tests.
  testInstrumentation(project(":instrumentation:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.testcontainers:kafka:${versions["org.testcontainers"]}")

  latestDepTestLibrary("org.apache.kafka:kafka-streams:2.+")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      isFailOnNoMatchingTests = false
    }
    include("**/KafkaStreamsSuppressReceiveSpansTest.*")
    jvmArgs("-Dotel.instrumentation.common.experimental.suppress-messaging-receive-spans=true")
  }

  test {
    dependsOn(testReceiveSpansDisabled)
    filter {
      excludeTestsMatching("KafkaStreamsSuppressReceiveSpansTest")
      isFailOnNoMatchingTests = false
    }
  }
}

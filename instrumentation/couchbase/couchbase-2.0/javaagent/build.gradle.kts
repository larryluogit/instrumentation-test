plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  // Version 2.7.5 and 2.7.8 were not released properly and muzzle cannot test against it causing failure.
  // So we have to skip them resulting in this verbose setup.
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.0.0,2.7.5)")
  }
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.7.6,2.7.8)")
  }
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[2.7.9,3.0.0)")
  }
  fail {
    group.set("com.couchbase.client")
    module.set("couchbase-client")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))
  implementation(project(":instrumentation:rxjava:rxjava-1.0:library"))

  library("com.couchbase.client:java-client:2.0.0")

  testImplementation(project(":instrumentation:couchbase:couchbase-common:testing"))

  latestDepTestLibrary("org.springframework.data:spring-data-couchbase:3.+")
  latestDepTestLibrary("com.couchbase.client:java-client:2.+")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=true")
}

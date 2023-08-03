plugins {
  id("otel.library-instrumentation")
  id("otel.animalsniffer-conventions")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.rxjava.v2_0")

dependencies {
  library("io.reactivex.rxjava2:rxjava:2.1.3")
  implementation(project(":instrumentation-annotations-support"))

  testImplementation(project(":instrumentation:rxjava:rxjava-2.0:testing"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17, uses spock Mock that needs access to jdk internals
  jvmArgs("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dio.opentelemetry.context.enableStrictContext=false")
}

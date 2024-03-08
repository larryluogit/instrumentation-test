plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("com.graphql-java:graphql-java:12.0")
  implementation(project(":instrumentation:graphql-java:graphql-java-common:library"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common:testing"))
}

plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("org.codehaus.groovy:groovy-all")
  testImplementation("org.spockframework:spock-core")

  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation:couchbase:couchbase-2-common:javaagent"))
  testImplementation("com.couchbase.client:java-client:2.5.0")
}

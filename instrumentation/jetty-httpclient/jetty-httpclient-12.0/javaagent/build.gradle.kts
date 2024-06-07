plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.eclipse.jetty")
    module.set("jetty-client")
    versions.set("[12,)")
  }
}

dependencies {
  implementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:library"))

  library("org.eclipse.jetty:jetty-client:12.0.0")

  testImplementation(project(":instrumentation:jetty-httpclient:jetty-httpclient-12.0:testing"))
}

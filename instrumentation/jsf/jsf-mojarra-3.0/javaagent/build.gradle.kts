plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[3,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("jakarta.faces:jakarta.faces-api:3.0.0")
  library("jakarta.el:jakarta.el-api:4.0.0")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))

  testLibrary("org.glassfish:jakarta.faces:3.0.4")
  latestDepTestLibrary("org.glassfish:jakarta.faces:3.+")
  // JSF 4+ requires CDI instead of BeanManager, the test should be upgraded first
  // latestDepTestLibrary("org.glassfish:jakarta.faces:4.+")

  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))
}

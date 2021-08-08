plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    name.set("webflux_5.0.0+_with_netty_0.8.0")
    group.set("org.springframework")
    module.set("spring-webflux")
    versions.set("[5.0.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor.netty:reactor-netty:0.8.0.RELEASE")
  }

  pass {
    name.set("webflux_5.0.0_with_ipc_0.7.0")
    group.set("org.springframework")
    module.set("spring-webflux")
    versions.set("[5.0.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")
  }

  pass {
    name.set("netty_0.8.0+_with_spring-webflux:5.1.0")
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[0.8.0.RELEASE,)")
    extraDependency("org.springframework:spring-webflux:5.1.0.RELEASE")
  }

  pass {
    name.set("ipc_0.7.0+_with_spring-webflux:5.0.0")
    group.set("io.projectreactor.ipc")
    module.set("reactor-netty")
    versions.set("[0.7.0.RELEASE,)")
    extraDependency("org.springframework:spring-webflux:5.0.0.RELEASE")
  }
}

dependencies {
  implementation(project(":instrumentation:spring:spring-webflux-5.0:library"))
  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")
  compileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor-netty:reactor-netty-1.0:javaagent"))

  // Compile with both old and new netty packages since our test references both for old and
  // latest dep tests.
  testCompileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")
  testCompileOnly("io.projectreactor.netty:reactor-netty-http:1.0.7")

  testLibrary("org.springframework.boot:spring-boot-starter-webflux:2.5.0")
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.0")
  testLibrary("org.springframework.boot:spring-boot-starter-reactor-netty:2.5.0")
  testImplementation("org.spockframework:spock-spring:1.1-groovy-2.4")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.spring-webflux.experimental-span-attributes=true")
  // TODO(trask): try removing this if/when latest webflux instrumentations changes prove out
  // TODO(anuraaga): There is no actual context leak - it just seems that the server-side does not
  // fully complete processing before the test cases finish, which is when we check for context
  // leaks. Adding Thread.sleep(1000) just before checking for leaks allows it to pass but is not
  // a good approach. Come up with a better one and enable this.
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")

//  jvmArgs("-Xint")
//  jvmArgs("-Dio.netty.defaultPromise.maxListenerStackDepth=1")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

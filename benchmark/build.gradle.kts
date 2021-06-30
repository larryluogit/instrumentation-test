plugins {
  id("me.champeau.jmh")
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

dependencies {
  jmh platform(project(":dependencyManagement"))

  jmh "io.opentelemetry:opentelemetry-api"
  jmh "net.bytebuddy:byte-buddy-agent"

  jmh project(':instrumentation-api')
  jmh project(':javaagent-api')
  jmh project(':javaagent-tooling')
  jmh project(':javaagent-extension-api')

  jmh "com.github.ben-manes.caffeine:caffeine"

  jmh 'javax.servlet:javax.servlet-api:4.0.1'
  jmh 'com.google.http-client:google-http-client:1.19.0'
  jmh 'org.eclipse.jetty:jetty-server:9.4.1.v20170120'
  jmh 'org.eclipse.jetty:jetty-servlet:9.4.1.v20170120'

  // used to provide lots of classes for TypeMatchingBenchmark
  jmh 'org.springframework:spring-web:4.3.28.RELEASE'
}

jmh {
  profilers = ['io.opentelemetry.benchmark.UsedMemoryProfiler', 'gc']

  duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE

  def jmhIncludeSingleClass = project.findProperty('jmhIncludeSingleClass')
  if (jmhIncludeSingleClass != null) {
    includes = [jmhIncludeSingleClass]
  }
}

tasks.named('jmh').configure {
  dependsOn(':javaagent:shadowJar')
}

/*
If using libasyncProfiler, use the following to generate nice svg flamegraphs.
sed '/unknown/d' benchmark/build/reports/jmh/profiler.txt | sed '/^thread_start/d' | sed '/not_walkable/d' > benchmark/build/reports/jmh/profiler-cleaned.txt
(using https://github.com/brendangregg/FlameGraph)
./flamegraph.pl --color=java benchmark/build/reports/jmh/profiler-cleaned.txt > benchmark/build/reports/jmh/jmh-main.svg
 */


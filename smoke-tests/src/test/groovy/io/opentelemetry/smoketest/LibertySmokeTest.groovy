/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import io.opentelemetry.proto.trace.v1.Span
import okhttp3.Request
import spock.lang.Shared
import spock.lang.Unroll

import java.util.jar.Attributes
import java.util.jar.JarFile

class LibertySmokeTest extends AppServerTest {
  //TODO run more configurations
  @Shared
  String jdk = 8
  @Shared
  String serverVersion = "20.0.0.12"

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:liberty-${serverVersion}-jdk$jdk-20201215.422527843"
  }

  boolean skipStartTarget() {
    true
  }

  def setupSpec() {
    startTarget(jdk, serverVersion)
  }

  def cleanupSpec() {
    stopTarget()
  }

  @Unroll
  def "#appServer test static file found on JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/hello.txt"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds
    String responseBody = response.body().string()

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response contains Hello"
    responseBody.contains("Hello")

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName('HTTP GET') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer test static file not found on JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/file-that-does-not-exist"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response code is 404"
    response.code() == 404

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName('HTTP GET') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer test request for WEB-INF/web.xml on JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/WEB-INF/web.xml"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response code is 404"
    response.code() == 404

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName('HTTP GET') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer test request with error JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/exception"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response code is 500"
    response.code() == 500

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName('/app/exception') == 1

    and: "There is one exception"
    traces.countFilteredEventAttributes('exception.message', 'This is expected') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer test request outside deployed application JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds

    then: "There is one trace"
    traceIds.size() == 1

    and: "Response code is 404"
    response.code() == 404

    and: "There is one server span"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 1

    and: "Expected span names"
    traces.countSpansByName('HTTP GET') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 1

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Unroll
  def "#appServer async smoke test on JDK #jdk"(String appServer, String jdk) {
    String url = "http://localhost:${target.getMappedPort(8080)}/app/asyncgreeting"
    def request = new Request.Builder().url(url).get().build()
    def currentAgentVersion = new JarFile(agentPath).getManifest().getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION)

    when:
    def response = CLIENT.newCall(request).execute()
    TraceInspector traces = new TraceInspector(waitForTraces())
    Set<String> traceIds = traces.traceIds
    String responseBody = response.body().string()

    then: "There is one trace"
    traceIds.size() == 1

    and: "trace id is present in the HTTP headers as reported by the called endpoint"
    responseBody.contains(traceIds.find())

    and: "Server spans in the distributed trace"
    traces.countSpansByKind(Span.SpanKind.SPAN_KIND_SERVER) == 2

    and: "Expected span names"
    traces.countSpansByName('/app/asyncgreeting') == 1
    traces.countSpansByName('/app/headers') == 1

    and: "The span for the initial web request"
    traces.countFilteredAttributes("http.url", url) == 1

    and: "Client and server spans for the remote call"
    traces.countFilteredAttributes("http.url", "http://localhost:8080/app/headers") == 2

    and: "Number of spans tagged with current otel library version"
    traces.countFilteredResourceAttributes("telemetry.auto.version", currentAgentVersion) == 3

    and:
    traces.findResourceAttribute("os.name")
      .map { it.stringValue }
      .findAny()
      .isPresent()

    where:
    [appServer, jdk] << getTestParams()
  }

  @Override
  List<List<Object>> getTestParams() {
    return [
      [serverVersion, jdk]
    ]
  }
}

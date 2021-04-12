/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.base

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.test.server.http.TestHttpServer.httpServer
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicClientSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderParentClientSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.AttributesAssert
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.function.Consumer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable

@Unroll
abstract class HttpClientTest<REQUEST> extends InstrumentationSpecification {
  protected static final BODY_METHODS = ["POST", "PUT"]
  protected static final CONNECT_TIMEOUT_MS = 5000
  protected static final BASIC_AUTH_KEY = "custom-authorization-header"
  protected static final BASIC_AUTH_VAL = "plain text auth token"

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        handleDistributedRequest()
        String msg = "Hello."
        response.status(200).id(request.getHeader("test-request-id")).send(msg)
      }
      prefix("client-error") {
        handleDistributedRequest()
        String msg = "Invalid RQ"
        response.status(400).send(msg)
      }
      prefix("error") {
        handleDistributedRequest()
        String msg = "Sorry."
        response.status(500).send(msg)
      }
      prefix("redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/success").toURL().toString())
      }
      prefix("another-redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/redirect").toURL().toString())
      }
      prefix("circular-redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/circular-redirect").toURL().toString())
      }
      prefix("secured") {
        handleDistributedRequest()
        if (request.headers.get(BASIC_AUTH_KEY) == BASIC_AUTH_VAL) {
          response.status(200).send("secured string under basic auth")
        } else {
          response.status(401).send("Unauthorized")
        }
      }
      prefix("to-secured") {
        handleDistributedRequest()
        redirect(server.address.resolve("/secured").toURL().toString())
      }
    }
  }

  // ideally private, but then groovy closures in this class cannot find them
  final int doRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = buildRequest(method, uri, headers)
    return sendRequest(request, method, uri, headers)
  }

  // ideally private, but then groovy closures in this class cannot find them
  final int doReusedRequest(String method, URI uri, Map<String, String> headers = [:]) {
    def request = buildRequest(method, uri, headers)
    sendRequest(request, method, uri, headers)
    return sendRequest(request, method, uri, headers)
  }

  // ideally private, but then groovy closures in this class cannot find them
  final void doRequestWithCallback(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    def request = buildRequest(method, uri, headers)
    sendRequestWithCallback(request, method, uri, headers, callback)
  }

  /**
   * Build the request to be passed to
   * {@link #sendRequest(java.lang.Object, java.lang.String, java.net.URI, java.util.Map)}.
   *
   * By splitting this step out separate from {@code sendRequest}, tests and re-execute the same
   * request a second time to verify that the traceparent header is not added multiple times to the
   * request, and that the last one wins
   * ({@link io.opentelemetry.instrumentation.test.server.http.TestHttpServer) has explicit logic
   * to throw exception if there are multiple).
   */
  abstract REQUEST buildRequest(String method, URI uri, Map<String, String> headers)

  /**
   * Make the request and return the status code of the response synchronously. Some clients, e.g.,
   * HTTPUrlConnection only support synchronous execution without callbacks, and many offer a
   * dedicated API for invoking synchronously, such as OkHttp's execute method. When implementing
   * this method, such an API should be used and the HTTP status code of the response returned,
   * for example:
   *
   * @Override
   * int sendRequest(Request request, String method, URI uri, Map<String, String headers = [:]) {
   *   HttpResponse response = client.execute(request)
   *   return response.statusCode()
   * }
   *
   * If there is no synchronous API available at all, for example as in Vert.X, a CompletableFuture
   * can be used to block on a result, for example:
   *
   * @Override
   * int sendRequest(Request request, String method, URI uri, Map<String, String> headers) {
   *   CompletableFuture<Integer> future = new CompletableFuture<>(
   *   sendRequestWithCallback(request, method, uri, headers) {
   *     future.complete(it.statusCode())
   *   }
   *   return future.get()
   * }
   */
  abstract int sendRequest(REQUEST request, String method, URI uri, Map<String, String> headers)

  /**
   * Make the request and return the status code of the response through the callback. This method
   * should be implemented if the client offers any request execution methods that accept a callback
   * which receives the response. This will generally be an API for asynchronous execution of a
   * request, such as OkHttp's enqueue method, but may also be a callback executed synchronously,
   * such as ApacheHttpClient's response handler callbacks. This method is used in tests to verify
   * the context is propagated correctly to such callbacks.
   *
   * @Override
   * void sendRequestWithCallback(Request request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
   *   // Hypothetical client accepting a callback
   *   client.executeAsync(request) {
   *     callback.accept(it.statusCode())
   *   }
   *
   *   // Hypothetical client returning a CompletableFuture
   *   client.executeAsync(request).thenAccept {
   *     callback.accept(it.statusCode())
   *   }
   * }
   *
   * If the client offers no APIs that accept callbacks, then this method should not be implemented
   * and instead, {@link #testCallback} should be implemented to return false.
   */
  void sendRequestWithCallback(REQUEST request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    // Must be implemented if testAsync is true
    throw new UnsupportedOperationException()
  }

  Integer statusOnRedirectError() {
    return null
  }

  String userAgent() {
    return null
  }

  List<AttributeKey<?>> extraAttributes() {
    []
  }

  def "basic #method request #url"() {
    when:
    def status = doRequest(method, url)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        clientSpan(it, 0, null, method, url)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    path << ["/success", "/success?with=params"]

    method = "GET"
    url = server.address.resolve(path)
  }

  def "basic #method request with parent"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method)
        serverSpan(it, 2 + extraClientSpans(), span(1 + extraClientSpans()))
      }
    }

    where:
    method << BODY_METHODS
  }

  def "should suppress nested CLIENT span if already under parent CLIENT span"() {
    given:
    assumeTrue(testWithClientParent())

    when:
    def status = runUnderParentClientSpan {
      doRequest(method, server.address.resolve("/success"))
    }

    then:
    status == 200
    // there should be 2 separate traces since the nested CLIENT span is suppressed
    // (and the span context propagation along with it)
    assertTraces(2) {
      traces.sort(orderByRootSpanKind(CLIENT, SERVER))

      trace(0, 1) {
        basicClientSpan(it, 0, "parent-client-span")
      }
      trace(1, 1) {
        serverSpan(it, 0)
      }
    }

    where:
    method << BODY_METHODS
  }


  //FIXME: add tests for POST with large/chunked data

  def "trace request without propagation"() {
    when:
    def status = runUnderTrace("parent") {
      doRequest(method, server.address.resolve("/success"), ["is-test-server": "false"])
    }

    then:
    status == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method)
      }
    }

    where:
    method = "GET"
  }

  def "trace request with callback and parent"() {
    given:
    assumeTrue(testCallback())
    assumeTrue(testCallbackWithParent())

    def status = new BlockingVariable<Integer>()

    when:
    runUnderTrace("parent") {
      doRequestWithCallback(method, server.address.resolve("/success"), ["is-test-server": "false"]) {
        runUnderTrace("child") {}
        status.set(it)
      }
    }

    then:
    status.get() == 200
    // only one trace (client).
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        basicSpan(it, 0, "parent")
        clientSpan(it, 1, span(0), method)
        basicSpan(it, 2 + extraClientSpans(), "child", span(0))
      }
    }

    where:
    method = "GET"
  }

  def "trace request with callback and no parent"() {
    given:
    assumeTrue(testCallback())

    def status = new BlockingVariable<Integer>()

    when:
    doRequestWithCallback(method, server.address.resolve("/success"), ["is-test-server": "false"]) {
      runUnderTrace("callback") {
      }
      status.set(it)
    }

    then:
    status.get() == 200
    // only one trace (client).
    assertTraces(2) {
      trace(0, 1 + extraClientSpans()) {
        clientSpan(it, 0, null, method)
      }
      trace(1, 1) {
        basicSpan(it, 0, "callback")
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 1 redirect"() {
    // TODO quite a few clients create an extra span for the redirect
    // This test should handle both types or we should unify how the clients work

    given:
    assumeTrue(testRedirects())
    def uri = server.address.resolve("/redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        clientSpan(it, 0, null, method, uri)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with 2 redirects"() {
    given:
    assumeTrue(testRedirects())
    def uri = server.address.resolve("/another-redirect")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 4 + extraClientSpans()) {
        clientSpan(it, 0, null, method, uri)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 3 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "basic #method request with circular redirects"() {
    given:
    assumeTrue(testRedirects() && testCircularRedirects())
    def uri = server.address.resolve("/circular-redirect")

    when:
    doRequest(method, uri)//, ["is-test-server": "false"])

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 1 + extraClientSpans() + maxRedirects()) {
        clientSpan(it, 0, null, method, uri, statusOnRedirectError(), thrownException)
        def start = 1 + extraClientSpans()
        for (int i = start; i < maxRedirects() + start; i++) {
          serverSpan(it, i, span(extraClientSpans()))
        }
      }
    }

    where:
    method = "GET"
  }

  def "redirect #method to secured endpoint copies auth header"() {
    given:
    assumeTrue(testRedirects())
    def uri = server.address.resolve("/to-secured")

    when:

    def status = doRequest(method, uri, [(BASIC_AUTH_KEY): BASIC_AUTH_VAL])

    then:
    status == 200
    assertTraces(1) {
      trace(0, 3 + extraClientSpans()) {
        clientSpan(it, 0, null, method, uri)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
        serverSpan(it, 2 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    method = "GET"
  }

  def "reuse request"() {
    given:
    assumeTrue(testReusedRequest())

    when:
    def status = doReusedRequest(method, url)

    then:
    status == 200
    assertTraces(2) {
      trace(0, 2 + extraClientSpans()) {
        clientSpan(it, 0, null, method, url)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
      }
      trace(1, 2 + extraClientSpans()) {
        clientSpan(it, 0, null, method, url)
        serverSpan(it, 1 + extraClientSpans(), span(extraClientSpans()))
      }
    }

    where:
    path = "/success"
    method = "GET"
    url = server.address.resolve(path)
  }

  def "connection error (unopened port)"() {
    given:
    assumeTrue(testConnectionFailure())
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent", null, thrownException)
        clientSpan(it, 1, span(0), method, uri, null, thrownException)
      }
    }

    where:
    method = "GET"
  }

  def "connection error dropped request"() {
    given:
    assumeTrue(testRemoteConnection())
    // https://stackoverflow.com/a/100859
    def uri = new URI("http://www.google.com:81/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent", null, thrownException)
        clientSpan(it, 1, span(0), method, uri, null, thrownException)
      }
    }

    where:
    method = "HEAD"
  }

  def "connection error non routable address"() {
    given:
    assumeTrue(testRemoteConnection())
    def uri = new URI("https://192.0.2.1/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex
    assertTraces(1) {
      trace(0, 2 + extraClientSpans()) {
        basicSpan(it, 0, "parent", null, thrownException)
        clientSpan(it, 1, span(0), method, uri, null, thrownException)
      }
    }

    where:
    method = "HEAD"
  }

  // IBM JVM has different protocol support for TLS
  @Requires({ !System.getProperty("java.vm.name").contains("IBM J9 VM") })
  def "test https request"() {
    given:
    assumeTrue(testRemoteConnection())
    def uri = new URI("https://www.google.com/")

    when:
    def status = doRequest(method, uri)

    then:
    status == 200
    assertTraces(1) {
      trace(0, 1 + extraClientSpans()) {
        clientSpan(it, 0, null, method, uri)
      }
    }

    where:
    method = "HEAD"
  }

  /**
   * This test fires a large number of concurrent requests.
   * Each request first hits a HTTP server and then makes another client request.
   * The goal of this test is to verify that in highly concurrent environment our instrumentations
   * for http clients (especially inherently concurrent ones, such as Netty or Reactor) correctly
   * propagate trace context.
   */
  def "high concurrency test"() {
    setup:
    assumeTrue(testCausality())
    int count = 50
    def method = 'GET'
    def url = server.address.resolve("/success")

    def latch = new CountDownLatch(1)

    def pool = Executors.newFixedThreadPool(4)

    when:
    count.times { index ->
      def job = {
        latch.await()
        runUnderTrace("Parent span " + index) {
          Span.current().setAttribute("test.request.id", index)
          doRequest(method, url, ["test-request-id": index.toString()])
        }
      }
      pool.submit(job)
    }
    latch.countDown()

    then:
    assertTraces(count) {
      count.times { idx ->
        trace(idx, 3) {
          def rootSpan = it.span(0)
          //Traces can be in arbitrary order, let us find out the request id of the current one
          def requestId = Integer.parseInt(rootSpan.name.substring("Parent span ".length()))

          basicSpan(it, 0, "Parent span " + requestId, null, null) {
            it."test.request.id" requestId
          }
          clientSpan(it, 1, span(0), method, url)
          serverSpan(it, 2, span(1)) {
            it."test.request.id" requestId
          }
        }
      }
    }
  }

  /**
   * Almost similar to the "high concurrency test" test above, but all requests use the same single
   * connection.
   */
  def "high concurrency test on single connection"() {
    setup:
    def singleConnection = createSingleConnection(server.address.host, server.address.port)
    assumeTrue(singleConnection != null)
    int count = 50
    def method = 'GET'
    def path = "/success"
    def url = server.address.resolve(path)

    def latch = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(4)

    when:
    count.times { index ->
      def job = {
        latch.await()
        runUnderTrace("Parent span " + index) {
          Span.current().setAttribute("test.request.id", index)
          singleConnection.doRequest(path, [(SingleConnection.REQUEST_ID_HEADER): index.toString()])
        }
      }
      pool.submit(job)
    }
    latch.countDown()

    then:
    assertTraces(count) {
      count.times { idx ->
        trace(idx, 3) {
          def rootSpan = it.span(0)
          //Traces can be in arbitrary order, let us find out the request id of the current one
          def requestId = Integer.parseInt(rootSpan.name.substring("Parent span ".length()))

          basicSpan(it, 0, "Parent span " + requestId, null, null) {
            it."test.request.id" requestId
          }
          clientSpan(it, 1, span(0), method, url)
          serverSpan(it, 2, span(1)) {
            it."test.request.id" requestId
          }
        }
      }
    }
  }

  //This method should create either a single connection to the target uri or a http client
  //which is guaranteed to use the same connection for all requests
  SingleConnection createSingleConnection(String host, int port) {
    return null
  }

  // parent span must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null, String httpFlavor = "1.1") {
    def userAgent = userAgent()
    def extraAttributes = extraAttributes()
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name expectedOperationName(method)
      kind CLIENT
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }
      attributes {
        "${SemanticAttributes.NET_TRANSPORT.key}" "IP.TCP"
        if (uri.port == UNUSABLE_PORT) {
          // TODO(anuraaga): For the unusable port, there isn't actually a peer so we shouldn't be
          // filling in peer information but some instrumentation does so based on the URL itself
          // which is present in HTTP attributes. We should fix this.
          "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it == uri.host }
          "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || it == UNUSABLE_PORT }
        } else {
          "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
          "${SemanticAttributes.NET_PEER_PORT.key}" uri.port > 0 ? uri.port : { it == null || it == 443 }
        }
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" } // Optional
        "${SemanticAttributes.HTTP_URL.key}" { it == "${uri}" || it == "${removeFragment(uri)}" }
        "${SemanticAttributes.HTTP_METHOD.key}" method
        "${SemanticAttributes.HTTP_FLAVOR.key}" httpFlavor
        if (userAgent) {
          "${SemanticAttributes.HTTP_USER_AGENT.key}" { it.startsWith(userAgent) }
        }
        if (status) {
          "${SemanticAttributes.HTTP_STATUS_CODE.key}" status
        }

        if (extraAttributes.contains(SemanticAttributes.HTTP_HOST)) {
          "${SemanticAttributes.HTTP_HOST}" "localhost:${uri.port}"
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH}" Long
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH)) {
          "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH}" Long
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_SCHEME)) {
          "${SemanticAttributes.HTTP_SCHEME}" "http"
        }
        if (extraAttributes.contains(SemanticAttributes.HTTP_TARGET)) {
          "${SemanticAttributes.HTTP_TARGET}" uri.path + "${uri.query != null ? "?${uri.query}" : ""}"
        }
      }
    }
  }

  void serverSpan(TraceAssert traces, int index, Object parentSpan = null,
                  @ClosureParams(value = SimpleType, options = ['io.opentelemetry.instrumentation.test.asserts.AttributesAssert'])
                  @DelegatesTo(value = AttributesAssert, strategy = Closure.DELEGATE_FIRST) Closure additionAttributesAssert = null) {
    traces.span(index) {
      name "test-http-server"
      kind SERVER
      errored false
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      if (additionAttributesAssert != null) {
        attributes(additionAttributesAssert)
      }
    }
  }

  String expectedOperationName(String method) {
    return method != null ? "HTTP $method" : "HTTP request"
  }

  int extraClientSpans() {
    0
  }

  boolean testWithClientParent() {
    true
  }

  boolean testRedirects() {
    true
  }

  boolean testCircularRedirects() {
    true
  }

  // maximum number of redirects that http client follows before giving up
  int maxRedirects() {
    2
  }

  boolean testReusedRequest() {
    true
  }

  boolean testConnectionFailure() {
    true
  }

  boolean testRemoteConnection() {
    true
  }

  boolean testCausality() {
    true
  }

  boolean testCallback() {
    return true
  }

  boolean testCallbackWithParent() {
    // FIXME: this hack is here because callback with parent is broken in play-ws when the stream()
    // function is used.  There is no way to stop a test from a derived class hence the flag
    true
  }

  URI removeFragment(URI uri) {
    return new URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, null)
  }
}

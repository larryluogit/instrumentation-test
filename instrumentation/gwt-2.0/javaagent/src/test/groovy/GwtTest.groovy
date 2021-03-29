/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.openqa.selenium.chrome.ChromeOptions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import spock.lang.Shared

class GwtTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {
  private static final Logger logger = LoggerFactory.getLogger(GwtTest)

  @Shared
  BrowserWebDriverContainer<?> chrome

  @Override
  Server startServer(int port) {
    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    webAppContext.setBaseResource(Resource.newResource(new File("build/testapp/war")))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  def setupSpec() {
    Testcontainers.exposeHostPorts(port)

    chrome = new BrowserWebDriverContainer<>()
      .withCapabilities(new ChromeOptions())
      .withLogConsumer(new Slf4jLogConsumer(logger))
    chrome.start()

    address = new URI("http://host.testcontainers.internal:$port" + getContextPath() + "/")
  }

  def cleanupSpec() {
    chrome?.stop()
  }

  @Override
  String getContextPath() {
    return "/xyz"
  }

  def getDriver() {
    def driver = chrome.getWebDriver()
    driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS)
    return driver
  }

  def "test gwt"() {
    setup:
    def driver = getDriver()

    // fetch the test page
    driver.get(address.resolve("greeting.html").toString())

    expect:
    // wait for page to load
    driver.findElementByClassName("greeting.button")
    assertTraces(4) {
      // /xyz/greeting.html
      trace(0, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/greeting/greeting.nocache.js
      trace(1, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/greeting/1B105441581A8F41E49D5DF3FB5B55BA.cache.html
      trace(2, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /favicon.ico
      trace(3, 1) {
        serverSpan(it, 0, "HTTP GET")
      }
    }
    clearExportedData()

    when:
    // click a button to trigger calling java code
    driver.findElementByClassName("greeting.button").click()

    then:
    // wait for response
    "Hello, Otel" == driver.findElementByClassName("message.received").getText()
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/greeting/greet")
        basicSpan(it, 1, "MessageServiceImpl.sendMessage", span(0))
      }
    }
    clearExportedData()

    when:
    // click a button to trigger calling java code
    driver.findElementByClassName("error.button").click()

    then:
    // wait for response
    "Error" == driver.findElementByClassName("error.received").getText()
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/greeting/greet")
        basicSpan(it, 1, "MessageServiceImpl.sendMessage", span(0), new IllegalArgumentException())
      }
    }

    cleanup:
    driver.close()
  }

  static serverSpan(TraceAssert trace, int index, String spanName) {
    trace.span(index) {
      hasNoParent()

      name spanName
      kind SpanKind.SERVER
    }
  }

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
      .header("User-Agent", TEST_USER_AGENT)
      .header("X-Forwarded-For", TEST_CLIENT_IP)
  }
}

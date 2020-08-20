/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.api.tracer

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride
import io.opentelemetry.context.propagation.HttpTextFormat
import io.opentelemetry.instrumentation.api.decorator.HttpStatusConverter
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Shared


class HttpClientTracerTest extends BaseTracerTest {

  @Shared
  def testUrl = new URI("http://myhost:123/somepath")

  @Shared
  def testUserAgent = "Apache HttpClient"

  def "test onRequest"() {
    setup:
    def tracer = newTracer()

    when:
    tracer.onRequest(span, req)

    then:
    if (req) {
      1 * span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), req.method)
      1 * span.setAttribute(SemanticAttributes.HTTP_URL.key(), "$req.url")
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME.key(), req.url.host)
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), req.url.port)
      1 * span.setAttribute(SemanticAttributes.HTTP_USER_AGENT.key(), req["User-Agent"])
    }
    0 * _

    where:
    req << [
      null,
      [method: "test-method", url: testUrl, "User-Agent": testUserAgent]
    ]
  }

  def "test onRequest with mapped peer"() {
    setup:
    def tracer = newTracer()
    def req = [method: "test-method", url: testUrl, "User-Agent": testUserAgent]

    when:
    withConfigOverride(
      "endpoint.peer.service.mapping",
      "myhost=reservation-service") {
      tracer.onRequest(span, req)
    }

    then:
    if (req) {
      1 * span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), req.method)
      1 * span.setAttribute(SemanticAttributes.HTTP_URL.key(), "$req.url")
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME.key(), req.url.host)
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), req.url.port)
      1 * span.setAttribute("peer.service", "reservation-service")
      1 * span.setAttribute(SemanticAttributes.HTTP_USER_AGENT.key(), req["User-Agent"])
    }
    0 * _
  }

  def "test url handling for #url"() {
    setup:
    def tracer = newTracer()

    when:
    withConfigOverride(io.opentelemetry.instrumentation.api.config.Config.HTTP_CLIENT_TAG_QUERY_STRING, "$tagQueryString") {
      tracer.onRequest(span, req)
    }

    then:
    if (expectedUrl) {
      1 * span.setAttribute(SemanticAttributes.HTTP_URL.key(), expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setAttribute(io.opentelemetry.instrumentation.api.MoreAttributes.HTTP_QUERY, expectedQuery)
      1 * span.setAttribute(io.opentelemetry.instrumentation.api.MoreAttributes.HTTP_FRAGMENT, expectedFragment)
    }
    1 * span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), null)
    if (hostname) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME.key(), hostname)
    }
    if (port) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), port)
    }
    0 * _

    where:
    tagQueryString | url                                                   | expectedUrl                                     | expectedQuery      | expectedFragment      | hostname | port
    false          | null                                                  | null                                            | null               | null                  | null     | null
    false          | ""                                                    | "/"                                             | ""                 | null                  | null     | null
    false          | "/path?query"                                         | "/path?query"                                   | ""                 | null                  | null     | null
    false          | "https://host:0"                                      | "https://host/"                                 | ""                 | null                  | "host"   | null
    false          | "https://host/path"                                   | "https://host/path"                             | ""                 | null                  | "host"   | null
    false          | "http://host:99/path?query#fragment"                  | "http://host:99/path?query#fragment"            | ""                 | null                  | "host"   | 99
    true           | null                                                  | null                                            | null               | null                  | null     | null
    true           | ""                                                    | "/"                                             | null               | null                  | null     | null
    true           | "/path?encoded+%28query%29%3F"                        | "/path?encoded+(query)?"                        | "encoded+(query)?" | null                  | null     | null
    true           | "https://host:0"                                      | "https://host/"                                 | null               | null                  | "host"   | null
    true           | "https://host/path"                                   | "https://host/path"                             | null               | null                  | "host"   | null
    true           | "http://host:99/path?query#encoded+%28fragment%29%3F" | "http://host:99/path?query#encoded+(fragment)?" | "query"            | "encoded+(fragment)?" | "host"   | 99

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onResponse"() {
    setup:
    def tracer = newTracer()

    when:
    tracer.onResponse(span, resp)

    then:
    if (status) {
      1 * span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.key(), status)
      1 * span.setStatus(HttpStatusConverter.statusFromHttpStatus(status))
    }
    0 * _

    where:
    status | resp
    200    | [status: 200]
    399    | [status: 399]
    400    | [status: 400]
    499    | [status: 499]
    500    | [status: 500]
    500    | [status: 500]
    600    | [status: 600]
    null   | [status: null]
    null   | null
  }

  def "test assert null span"() {
    setup:
    def tracer = newTracer()

    when:
    tracer.onRequest((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    tracer.onResponse((Span) null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newTracer() {
    return new HttpClientTracer<Map, Map, Map>() {

      @Override
      protected String method(Map m) {
        return m.method
      }

      @Override
      protected URI url(Map m) {
        return m.url
      }

      @Override
      protected Integer status(Map m) {
        return m.status
      }

      @Override
      protected String requestHeader(Map m, String name) {
        return m[name]
      }

      @Override
      protected String responseHeader(Map m, String name) {
        return m[name]
      }

      @Override
      protected HttpTextFormat.Setter<Map> getSetter() {
        return null
      }

      @Override
      protected String getInstrumentationName() {
        return "HttpClientTracerTest"
      }
    }
  }
}

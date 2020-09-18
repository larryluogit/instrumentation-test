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

package io.opentelemetry.instrumentation.api.tracer;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.decorator.HttpStatusConverter;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.TracingContextUtils;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpClientTracer<REQUEST, CARRIER, RESPONSE> extends BaseTracer {

  private static final Logger log = LoggerFactory.getLogger(HttpClientTracer.class);

  public static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected static final String USER_AGENT = "User-Agent";

  protected abstract String method(REQUEST request);

  @Nullable
  protected abstract URI url(REQUEST request) throws URISyntaxException;

  @Nullable
  protected String flavor(REQUEST request) {
    // This is de facto standard nowadays, so let us use it, unless overridden
    return "1.1";
  }

  protected abstract Integer status(RESPONSE response);

  @Nullable
  protected abstract String requestHeader(REQUEST request, String name);

  @Nullable
  protected abstract String responseHeader(RESPONSE response, String name);

  protected abstract TextMapPropagator.Setter<CARRIER> getSetter();

  protected HttpClientTracer() {
    super();
  }

  protected HttpClientTracer(Tracer tracer) {
    super(tracer);
  }

  public Span startSpan(REQUEST request) {
    return startSpan(request, -1);
  }

  public Span startSpan(REQUEST request, long startTimeNanos) {
    return startSpan(request, spanNameForRequest(request), startTimeNanos);
  }

  public Scope startScope(Span span, CARRIER carrier) {
    Context context = withSpan(span, Context.current());

    Setter<CARRIER> setter = getSetter();
    if (setter == null) {
      throw new IllegalStateException(
          "getSetter() not defined but calling startScope(), either getSetter must be implemented or the scope should be setup manually");
    }
    OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, carrier, setter);
    context = context.withValue(CONTEXT_CLIENT_SPAN_KEY, span);
    return withScopedContext(context);
  }

  public void end(Span span, RESPONSE response) {
    end(span, response, -1);
  }

  public void end(Span span, RESPONSE response, long endTimeNanos) {
    onResponse(span, response);
    super.end(span, endTimeNanos);
  }

  public void endExceptionally(Span span, RESPONSE response, Throwable throwable) {
    endExceptionally(span, response, throwable, -1);
  }

  public void endExceptionally(
      Span span, RESPONSE response, Throwable throwable, long endTimeNanos) {
    onResponse(span, response);
    super.endExceptionally(span, throwable, endTimeNanos);
  }

  /**
   * Returns a new client {@link Span} if there is no client {@link Span} in the current {@link
   * Context}, or an invalid {@link Span} otherwise.
   */
  private Span startSpan(REQUEST request, String name, long startTimeNanos) {
    Context context = Context.current();
    Span clientSpan = CONTEXT_CLIENT_SPAN_KEY.get(context);

    if (clientSpan != null) {
      // We don't want to create two client spans for a given client call, suppress inner spans.
      return DefaultSpan.getInvalid();
    }

    Span current = TracingContextUtils.getSpan(context);
    Span.Builder spanBuilder = tracer.spanBuilder(name).setSpanKind(Kind.CLIENT).setParent(current);
    if (startTimeNanos > 0) {
      spanBuilder.setStartTimestamp(startTimeNanos);
    }
    Span span = spanBuilder.startSpan();
    onRequest(span, request);
    return span;
  }

  protected Span onRequest(Span span, REQUEST request) {
    assert span != null;
    if (request != null) {
      SemanticAttributes.NET_TRANSPORT.set(span, "IP.TCP");
      SemanticAttributes.HTTP_METHOD.set(span, method(request));
      SemanticAttributes.HTTP_USER_AGENT.set(span, requestHeader(request, USER_AGENT));

      setFlavor(span, request);
      setUrl(span, request);
    }
    return span;
  }

  private void setFlavor(Span span, REQUEST request) {
    String flavor = flavor(request);
    if (flavor == null) {
      return;
    }

    String httpProtocolPrefix = "HTTP/";
    if (flavor.startsWith(httpProtocolPrefix)) {
      flavor = flavor.substring(httpProtocolPrefix.length());
    }

    SemanticAttributes.HTTP_FLAVOR.set(span, flavor);
  }

  private void setUrl(Span span, REQUEST request) {
    try {
      URI url = url(request);
      if (url != null) {
        NetPeerUtils.setNetPeer(span, url.getHost(), null, url.getPort());
        SemanticAttributes.HTTP_URL.set(span, url.toString());
      }
    } catch (Exception e) {
      log.debug("Error tagging url", e);
    }
  }

  protected Span onResponse(Span span, RESPONSE response) {
    assert span != null;
    if (response != null) {
      Integer status = status(response);
      if (status != null) {
        span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.key(), status);
        span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
      }
    }
    return span;
  }

  protected String spanNameForRequest(REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }
}

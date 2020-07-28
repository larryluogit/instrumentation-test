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

package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import static io.opentelemetry.OpenTelemetry.getPropagators;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreAttributes;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO In search for a better home package
public abstract class HttpServerTracer<REQUEST, CONNECTION, STORAGE> extends BaseTracer {

  private static final Logger log = LoggerFactory.getLogger(HttpServerTracer.class);

  public static final String CONTEXT_ATTRIBUTE = "io.opentelemetry.instrumentation.context";

  public HttpServerTracer() {
    super();
  }

  public HttpServerTracer(Tracer tracer) {
    super(tracer);
  }

  public Span startSpan(REQUEST request, CONNECTION connection, Method origin) {
    String spanName = spanNameForMethod(origin);
    return startSpan(request, connection, spanName);
  }

  public Span startSpan(REQUEST request, CONNECTION connection, String spanName) {
    return startSpan(request, connection, spanName, -1);
  }

  public Span startSpan(
      REQUEST request, CONNECTION connection, String spanName, long startTimestamp) {
    Span.Builder builder =
        tracer.spanBuilder(spanName).setSpanKind(SERVER).setParent(extract(request, getGetter()));

    if (startTimestamp >= 0) {
      builder.setStartTimestamp(startTimestamp);
    }

    Span span = builder.startSpan();
    onConnection(span, connection);
    onRequest(span, request);

    return span;
  }

  /**
   * Creates new scoped context with the given span.
   *
   * <p>Attaches new context to the request to avoid creating duplicate server spans.
   */
  public Scope startScope(Span span, STORAGE storage) {
    // TODO we could do this in one go, but TracingContextUtils.CONTEXT_SPAN_KEY is private
    Context newContext = withSpan(span, Context.current().withValue(CONTEXT_SERVER_SPAN_KEY, span));
    attachServerContext(newContext, storage);
    return withScopedContext(newContext);
  }

  // TODO should end methods remove SPAN attribute from request as well?
  public void end(Span span, int responseStatus) {
    end(span, responseStatus, -1);
  }

  // TODO should end methods remove SPAN attribute from request as well?
  public void end(Span span, int responseStatus, long timestamp) {
    setStatus(span, responseStatus);
    if (timestamp >= 0) {
      span.end(EndSpanOptions.builder().setEndTimestamp(timestamp).build());
    } else {
      span.end();
    }
  }

  /** Ends given span exceptionally with default response status code 500. */
  public void endExceptionally(Span span, Throwable throwable) {
    endExceptionally(span, throwable, 500);
  }

  public void endExceptionally(Span span, Throwable throwable, int responseStatus) {
    endExceptionally(span, throwable, responseStatus, -1);
  }

  public void endExceptionally(Span span, Throwable throwable, int responseStatus, long timestamp) {
    if (responseStatus == 200) {
      // TODO I think this is wrong.
      // We must report that response status that was actually sent to end user
      // We may change span status, but not http_status attribute
      responseStatus = 500;
    }
    onError(span, unwrapThrowable(throwable));
    end(span, responseStatus, timestamp);
  }

  public Span getServerSpan(STORAGE storage) {
    Context attachedContext = getServerContext(storage);
    return attachedContext == null ? null : CONTEXT_SERVER_SPAN_KEY.get(attachedContext);
  }

  /**
   * Returns context stored to the given request-response-loop storage by {@link
   * #attachServerContext(Context, STORAGE)}.
   *
   * <p>May be null.
   */
  public abstract Context getServerContext(STORAGE storage);

  protected void onConnection(Span span, CONNECTION connection) {
    SemanticAttributes.NET_PEER_IP.set(span, peerHostIP(connection));
    Integer port = peerPort(connection);
    // Negative or Zero ports might represent an unset/null value for an int type.  Skip setting.
    if (port != null && port > 0) {
      SemanticAttributes.NET_PEER_PORT.set(span, port);
    }
  }

  // TODO use semantic attributes
  protected void onRequest(final Span span, final REQUEST request) {
    SemanticAttributes.HTTP_METHOD.set(span, method(request));

    // Copy of HttpClientDecorator url handling
    try {
      URI url = url(request);
      if (url != null) {
        StringBuilder urlBuilder = new StringBuilder();
        if (url.getScheme() != null) {
          urlBuilder.append(url.getScheme());
          urlBuilder.append("://");
        }
        if (url.getHost() != null) {
          urlBuilder.append(url.getHost());
          if (url.getPort() > 0 && url.getPort() != 80 && url.getPort() != 443) {
            urlBuilder.append(":");
            urlBuilder.append(url.getPort());
          }
        }
        String path = url.getPath();
        if (path.isEmpty()) {
          urlBuilder.append("/");
        } else {
          urlBuilder.append(path);
        }
        String query = url.getQuery();
        if (query != null) {
          urlBuilder.append("?").append(query);
        }
        String fragment = url.getFragment();
        if (fragment != null) {
          urlBuilder.append("#").append(fragment);
        }

        span.setAttribute(SemanticAttributes.HTTP_URL.key(), urlBuilder.toString());

        if (Config.get().isHttpServerTagQueryString()) {
          span.setAttribute(MoreAttributes.HTTP_QUERY, url.getQuery());
          span.setAttribute(MoreAttributes.HTTP_FRAGMENT, url.getFragment());
        }
      }
    } catch (final Exception e) {
      log.debug("Error tagging url", e);
    }
    // TODO set resource name from URL.
  }

  private <C> SpanContext extract(final C carrier, final HttpTextFormat.Getter<C> getter) {
    // Using Context.ROOT here may be quite unexpected, but the reason is simple.
    // We want either span context extracted from the carrier or invalid one.
    // We DO NOT want any span context potentially lingering in the current context.
    Context context = getPropagators().getHttpTextFormat().extract(Context.ROOT, carrier, getter);
    Span span = getSpan(context);
    return span.getContext();
  }

  private void setStatus(Span span, int status) {
    SemanticAttributes.HTTP_STATUS_CODE.set(span, status);
    // TODO status_message
    span.setStatus(HttpStatusConverter.statusFromHttpStatus(status));
  }

  protected abstract Integer peerPort(CONNECTION connection);

  protected abstract String peerHostIP(CONNECTION connection);

  protected abstract HttpTextFormat.Getter<REQUEST> getGetter();

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract String method(REQUEST request);

  /**
   * Stores given context in the given request-response-loop storage in implementation specific way.
   */
  protected abstract void attachServerContext(Context context, STORAGE storage);
}

package io.opentelemetry.auto.typed.server;

import io.opentelemetry.auto.typed.server.http.HttpServerTypedSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;

public class SampleHttpServerTypedSpan
    extends HttpServerTypedSpan<SampleHttpServerTypedSpan, String, String> {
  public SampleHttpServerTypedSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected SampleHttpServerTypedSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  protected SampleHttpServerTypedSpan onResponse(String o) {
    delegate.setStatus(Status.OK);
    return this;
  }

  @Override
  protected SampleHttpServerTypedSpan self() {
    return this;
  }
}

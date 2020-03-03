package io.opentelemetry.auto.typed.client;

import io.opentelemetry.auto.typed.client.http.HttpClientTypedSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;

public class SampleHttpClientTypedSpan
    extends HttpClientTypedSpan<SampleHttpClientTypedSpan, String, String> {
  public SampleHttpClientTypedSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected SampleHttpClientTypedSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  protected SampleHttpClientTypedSpan onResponse(String o) {
    delegate.setStatus(Status.OK);
    return this;
  }

  @Override
  protected SampleHttpClientTypedSpan self() {
    return this;
  }
}

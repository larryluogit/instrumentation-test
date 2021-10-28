/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Locale;
import org.restlet.Message;
import org.restlet.Request;
import org.restlet.util.Series;

final class RestletHeadersGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    return getHeaders(carrier).getNames();
  }

  @Override
  public String get(Request carrier, String key) {

    Series<?> headers = getHeaders(carrier);

    String value = headers.getFirstValue(key);
    if (value != null) {
      return value;
    }
    return headers.getFirstValue(key.toLowerCase(Locale.ROOT));
  }

  static Series<?> getHeaders(Message carrier) {
    return (Series<?>) carrier.getAttributes().get("org.restlet.http.headers");
  }
}

/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.couchbase.client;

import static io.opentelemetry.auto.instrumentation.couchbase.client.CouchbaseClientDecorator.DECORATE;
import static io.opentelemetry.trace.Span.Kind.CLIENT;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.instrumentation.rxjava.TracedOnSubscribe;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import rx.Observable;

public class CouchbaseOnSubscribe extends TracedOnSubscribe {
  private final String resourceName;
  private final String bucket;
  private final String query;

  public CouchbaseOnSubscribe(
      final Observable originalObservable,
      final Method method,
      final String bucket,
      final String query) {
    super(originalObservable, "couchbase.call", DECORATE, CLIENT);

    final Class<?> declaringClass = method.getDeclaringClass();
    final String className =
        declaringClass.getSimpleName().replace("CouchbaseAsync", "").replace("DefaultAsync", "");
    resourceName = className + "." + method.getName();
    this.bucket = bucket;
    this.query = query;
  }

  @Override
  protected void afterStart(final Span span) {
    super.afterStart(span);

    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);

    if (bucket != null) {
      span.setAttribute(Tags.DB_INSTANCE, bucket);
    }
    if (query != null) {
      span.setAttribute(Tags.DB_STATEMENT, query);
    }
  }
}

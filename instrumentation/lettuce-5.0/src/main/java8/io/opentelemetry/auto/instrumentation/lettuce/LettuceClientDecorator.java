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
package io.opentelemetry.auto.instrumentation.lettuce;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

public class LettuceClientDecorator extends DatabaseClientDecorator<RedisURI> {
  public static final LettuceClientDecorator DECORATE = new LettuceClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.lettuce-5.0");

  @Override
  protected String service() {
    return "redis";
  }

  @Override
  protected String getComponentName() {
    return "redis-client";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final RedisURI connection) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisURI connection) {
    return null;
  }

  @Override
  public Span onConnection(final Span span, final RedisURI connection) {
    if (connection != null) {
      span.setAttribute(MoreTags.NET_PEER_NAME, connection.getHost());
      span.setAttribute(MoreTags.NET_PEER_PORT, connection.getPort());

      span.setAttribute("db.redis.dbIndex", connection.getDatabase());
      span.setAttribute(
          MoreTags.RESOURCE_NAME,
          "CONNECT:"
              + connection.getHost()
              + ":"
              + connection.getPort()
              + "/"
              + connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  public Span onCommand(final Span span, final RedisCommand command) {
    final String commandName = LettuceInstrumentationUtil.getCommandName(command);
    span.setAttribute(
        MoreTags.RESOURCE_NAME, LettuceInstrumentationUtil.getCommandResourceName(commandName));
    return span;
  }
}

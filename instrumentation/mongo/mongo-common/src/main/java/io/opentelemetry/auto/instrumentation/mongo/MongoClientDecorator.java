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
package io.opentelemetry.auto.instrumentation.mongo;

import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanTypes;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

public class MongoClientDecorator extends DatabaseClientDecorator<CommandStartedEvent> {
  public static final MongoClientDecorator DECORATE = new MongoClientDecorator();

  // TODO use tracer names *.mongo-3.1, *.mongo-3.7, *.mongo-async-3.3 respectively in each module
  public static final Tracer TRACER =
      OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.mongo");

  @Override
  protected String service() {
    return "mongo";
  }

  @Override
  protected String getComponentName() {
    return "java-mongo";
  }

  @Override
  protected String getSpanType() {
    return SpanTypes.MONGO;
  }

  @Override
  protected String dbType() {
    return "mongo";
  }

  @Override
  protected String dbUser(final CommandStartedEvent event) {
    return null;
  }

  @Override
  protected String dbInstance(final CommandStartedEvent event) {
    // Use description if set.
    final ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription != null) {
      final ConnectionId connectionId = connectionDescription.getConnectionId();
      if (connectionId != null) {
        final ServerId serverId = connectionId.getServerId();
        if (serverId != null) {
          final ClusterId clusterId = serverId.getClusterId();
          if (clusterId != null) {
            final String description = clusterId.getDescription();
            if (description != null) {
              return description;
            }
          }
        }
      }
    }
    // Fallback to db name.
    return event.getDatabaseName();
  }

  public Span onStatement(final Span span, final BsonDocument statement) {

    // scrub the Mongo command so that parameters are removed from the string
    final BsonDocument scrubbed = scrub(statement);
    final String mongoCmd = scrubbed.toString();

    span.setAttribute(MoreTags.RESOURCE_NAME, mongoCmd);
    return onStatement(span, mongoCmd);
  }

  /**
   * The values of these mongo fields will not be scrubbed out. This allows the non-sensitive
   * collection names to be captured.
   */
  private static final List<String> UNSCRUBBED_FIELDS =
      Arrays.asList("ordered", "insert", "count", "find", "create");

  private static final BsonValue HIDDEN_CHAR = new BsonString("?");

  private static BsonDocument scrub(final BsonDocument origin) {
    final BsonDocument scrub = new BsonDocument();
    for (final Map.Entry<String, BsonValue> entry : origin.entrySet()) {
      if (UNSCRUBBED_FIELDS.contains(entry.getKey()) && entry.getValue().isString()) {
        scrub.put(entry.getKey(), entry.getValue());
      } else {
        final BsonValue child = scrub(entry.getValue());
        scrub.put(entry.getKey(), child);
      }
    }
    return scrub;
  }

  private static BsonValue scrub(final BsonArray origin) {
    final BsonArray scrub = new BsonArray();
    for (final BsonValue value : origin) {
      final BsonValue child = scrub(value);
      scrub.add(child);
    }
    return scrub;
  }

  private static BsonValue scrub(final BsonValue origin) {
    final BsonValue scrubbed;
    if (origin.isDocument()) {
      scrubbed = scrub(origin.asDocument());
    } else if (origin.isArray()) {
      scrubbed = scrub(origin.asArray());
    } else {
      scrubbed = HIDDEN_CHAR;
    }
    return scrubbed;
  }
}

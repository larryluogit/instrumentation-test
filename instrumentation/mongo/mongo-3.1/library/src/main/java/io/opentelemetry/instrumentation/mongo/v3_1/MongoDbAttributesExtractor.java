/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.checkerframework.checker.nullness.qual.Nullable;

class MongoDbAttributesExtractor extends DbAttributesExtractor<CommandStartedEvent, Void> {

  @Nullable private static final Method IS_TRUNCATED_METHOD;
  private static final String HIDDEN_CHAR = "?";

  static {
    IS_TRUNCATED_METHOD =
        Arrays.stream(JsonWriter.class.getMethods())
            .filter(method -> method.getName().equals("isTruncated"))
            .findFirst()
            .orElse(null);
  }

  private final int maxNormalizedQueryLength;
  @Nullable private final JsonWriterSettings jsonWriterSettings;

  MongoDbAttributesExtractor(int maxNormalizedQueryLength) {
    this.maxNormalizedQueryLength = maxNormalizedQueryLength;
    this.jsonWriterSettings = createJsonWriterSettings(maxNormalizedQueryLength);
  }

  @Override
  protected String system(CommandStartedEvent event) {
    return SemanticAttributes.DbSystemValues.MONGODB;
  }

  @Override
  protected @Nullable String user(CommandStartedEvent event) {
    return null;
  }

  @Override
  protected @Nullable String name(CommandStartedEvent event) {
    return event.getDatabaseName();
  }

  @Override
  protected @Nullable String connectionString(CommandStartedEvent event) {
    ConnectionDescription connectionDescription = event.getConnectionDescription();
    if (connectionDescription != null) {
      ServerAddress sa = connectionDescription.getServerAddress();
      if (sa != null) {
        // https://docs.mongodb.com/manual/reference/connection-string/
        String host = sa.getHost();
        int port = sa.getPort();
        if (host != null && port != 0) {
          return "mongodb://" + host + ":" + port;
        }
      }
    }
    return null;
  }

  @Override
  protected String statement(CommandStartedEvent event) {
    return sanitizeStatement(event.getCommand());
  }

  @Override
  protected @Nullable String operation(CommandStartedEvent event) {
    return event.getCommandName();
  }

  // TODO(anuraaga): Migrate off of StringWriter to avoid synchronization.
  // accessible to tests
  String sanitizeStatement(BsonDocument command) {
    StringWriter stringWriter = new StringWriter(128);
    // jsonWriterSettings is generally not null but could be due to security manager or unknown
    // API incompatibilities, which we can't detect by Muzzle because we use reflection.
    JsonWriter jsonWriter =
        jsonWriterSettings != null
            ? new JsonWriter(stringWriter, jsonWriterSettings)
            : new JsonWriter(stringWriter);
    writeScrubbed(command, jsonWriter, /* isRoot= */ true);
    // If using MongoDB driver >= 3.7, the substring invocation will be a no-op due to use of
    // JsonWriterSettings.Builder.maxLength in the static initializer for JSON_WRITER_SETTINGS
    StringBuffer buf = stringWriter.getBuffer();
    if (buf.length() <= maxNormalizedQueryLength) {
      return buf.toString();
    }
    return buf.substring(0, maxNormalizedQueryLength);
  }

  @Nullable
  private static JsonWriterSettings createJsonWriterSettings(int maxNormalizedQueryLength) {
    JsonWriterSettings settings = null;
    try {
      // The static JsonWriterSettings.builder() method was introduced in the 3.5 release
      Optional<Method> buildMethod =
          Arrays.stream(JsonWriterSettings.class.getMethods())
              .filter(method -> method.getName().equals("builder"))
              .findFirst();
      if (buildMethod.isPresent()) {
        Class<?> builderClass = buildMethod.get().getReturnType();
        Object builder = buildMethod.get().invoke(null, (Object[]) null);

        // The JsonWriterSettings.Builder.indent method was introduced in the 3.5 release,
        // but checking anyway
        Optional<Method> indentMethod =
            Arrays.stream(builderClass.getMethods())
                .filter(method -> method.getName().equals("indent"))
                .findFirst();
        if (indentMethod.isPresent()) {
          indentMethod.get().invoke(builder, false);
        }

        // The JsonWriterSettings.Builder.maxLength method was introduced in the 3.7 release
        Optional<Method> maxLengthMethod =
            Arrays.stream(builderClass.getMethods())
                .filter(method -> method.getName().equals("maxLength"))
                .findFirst();
        if (maxLengthMethod.isPresent()) {
          maxLengthMethod.get().invoke(builder, maxNormalizedQueryLength);
        }
        settings =
            (JsonWriterSettings)
                builderClass.getMethod("build", (Class<?>[]) null).invoke(builder, (Object[]) null);
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
      // Ignore
    }
    if (settings == null) {
      try {
        // Constructor removed in 4.0+ so use reflection. 4.0+ will have used the builder above.
        settings = JsonWriterSettings.class.getConstructor(Boolean.TYPE).newInstance(false);
      } catch (InstantiationException
          | IllegalAccessException
          | InvocationTargetException
          | NoSuchMethodException ignored) {
        // Ignore
      }
    }

    return settings;
  }

  private static boolean writeScrubbed(BsonDocument origin, JsonWriter writer, boolean isRoot) {
    writer.writeStartDocument();
    boolean firstField = true;
    for (Map.Entry<String, BsonValue> entry : origin.entrySet()) {
      writer.writeName(entry.getKey());
      // the first field of the root document is the command name, so we preserve its value
      // (which for most CRUD commands is the collection name)
      if (isRoot && firstField && entry.getValue().isString()) {
        writer.writeString(entry.getValue().asString().getValue());
      } else {
        if (writeScrubbed(entry.getValue(), writer)) {
          return true;
        }
      }
      firstField = false;
    }
    writer.writeEndDocument();
    return false;
  }

  private static boolean writeScrubbed(BsonArray origin, JsonWriter writer) {
    writer.writeStartArray();
    for (BsonValue value : origin) {
      if (writeScrubbed(value, writer)) {
        return true;
      }
    }
    writer.writeEndArray();
    return false;
  }

  private static boolean writeScrubbed(BsonValue origin, JsonWriter writer) {
    if (origin.isDocument()) {
      return writeScrubbed(origin.asDocument(), writer, /* isRoot= */ false);
    } else if (origin.isArray()) {
      return writeScrubbed(origin.asArray(), writer);
    } else {
      writer.writeString(HIDDEN_CHAR);
      return isTruncated(writer);
    }
  }

  private static boolean isTruncated(JsonWriter writer) {
    if (IS_TRUNCATED_METHOD == null) {
      return false;
    } else {
      try {
        return (boolean) IS_TRUNCATED_METHOD.invoke(writer, (Object[]) null);
      } catch (IllegalAccessException | InvocationTargetException ignored) {
        return false;
      }
    }
  }
}

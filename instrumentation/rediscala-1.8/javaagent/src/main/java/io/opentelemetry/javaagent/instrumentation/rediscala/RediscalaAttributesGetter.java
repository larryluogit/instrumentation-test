/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemValues.REDIS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import java.util.Locale;
import javax.annotation.Nullable;
import redis.RedisCommand;

final class RediscalaAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?>> {

  @Deprecated
  @Override
  public String getSystem(RedisCommand<?, ?> redisCommand) {
    return REDIS;
  }

  @Override
  public String getDbSystem(RedisCommand<?, ?> redisCommand) {
    return REDIS;
  }

  @Deprecated
  @Override
  @Nullable
  public String getUser(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getName(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Nullable
  @Override
  public String getDbNamespace(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getConnectionString(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Deprecated
  @Override
  @Nullable
  public String getStatement(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Nullable
  @Override
  public String getDbQueryText(RedisCommand<?, ?> redisCommand) {
    return null;
  }

  @Deprecated
  @Override
  public String getOperation(RedisCommand<?, ?> redisCommand) {
    return redisCommand.getClass().getSimpleName().toUpperCase(Locale.ROOT);
  }

  @Override
  public String getDbOperationName(RedisCommand<?, ?> redisCommand) {
    return redisCommand.getClass().getSimpleName().toUpperCase(Locale.ROOT);
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import com.google.auto.value.AutoValue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

  public abstract Connection getConnection();

  public abstract ProtocolCommand getCommand();

  public abstract List<byte[]> getArgs();

  public String getStringCommand() {
    ProtocolCommand command = getCommand();
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), StandardCharsets.UTF_8);
    }
  }

  public static JedisRequest create(
      Connection connection, ProtocolCommand command, List<byte[]> args) {
    return new AutoValue_JedisRequest(connection, command, args);
  }
}

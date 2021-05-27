/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.netty.buffer.ByteBuf;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

final class RedissonDbAttributesExtractor extends DbAttributesExtractor<RedissonRequest, Void> {

  private static final String UNKNOWN_COMMAND = "Redis Command";

  @Override
  protected String system(RedissonRequest request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Nullable
  @Override
  protected String user(RedissonRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String name(RedissonRequest request) {
    return null;
  }

  @Override
  protected String connectionString(RedissonRequest request) {
    return null;
  }

  @Override
  protected String statement(RedissonRequest request) {
    List<String> sanitizedStatements = sanitizeStatement(request);
    switch (sanitizedStatements.size()) {
      case 0:
        return UNKNOWN_COMMAND;
        // optimize for the most common case
      case 1:
        return sanitizedStatements.get(0);
      default:
        return String.join(";", sanitizedStatements);
    }
  }

  @Nullable
  @Override
  protected String operation(RedissonRequest request) {
    Object command = request.getCommand();
    if (command instanceof CommandData) {
      return ((CommandData<?, ?>) command).getCommand().getName();
    } else if (command instanceof CommandsData) {
      CommandsData commandsData = (CommandsData) command;
      if (commandsData.getCommands().size() == 1) {
        return commandsData.getCommands().get(0).getCommand().getName();
      }
    }
    return null;
  }

  private List<String> sanitizeStatement(RedissonRequest request) {
    Object command = request.getCommand();
    // get command
    if (command instanceof CommandsData) {
      List<CommandData<?, ?>> commands = ((CommandsData) command).getCommands();
      return commands.stream().map(this::normalizeSingleCommand).collect(Collectors.toList());
    } else if (command instanceof CommandData) {
      return singletonList(normalizeSingleCommand((CommandData<?, ?>) command));
    }
    return emptyList();
  }

  private String normalizeSingleCommand(CommandData<?, ?> command) {
    Object[] commandParams = command.getParams();
    List<Object> args = new ArrayList<>(commandParams.length + 1);
    if (command.getCommand().getSubName() != null) {
      args.add(command.getCommand().getSubName());
    }
    for (Object param : commandParams) {
      if (param instanceof ByteBuf) {
        try {
          // slice() does not copy the actual byte buffer, it only returns a readable/writable
          // "view" of the original buffer (i.e. read and write marks are not shared)
          ByteBuf buf = ((ByteBuf) param).slice();
          // state can be null here: no Decoders used by Codecs use it
          args.add(command.getCodec().getValueDecoder().decode(buf, null));
        } catch (Exception ignored) {
          args.add("?");
        }
      } else {
        args.add(param);
      }
    }
    return RedisCommandSanitizer.sanitize(command.getCommand().getName(), args);
  }
}

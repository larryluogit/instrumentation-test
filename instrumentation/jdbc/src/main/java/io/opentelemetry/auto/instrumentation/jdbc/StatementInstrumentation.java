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
package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.instrumentation.jdbc.JDBCDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jdbc.JDBCDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jdbc.JDBCUtils.connectionFromStatement;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class StatementInstrumentation extends Instrumenter.Default {

  public StatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.sql.Statement"));
  }

  @Override
  public String[] helperClassNames() {
    final List<String> helpers = new ArrayList<>(JDBCConnectionUrlParser.values().length + 9);

    helpers.add(packageName + ".DBInfo");
    helpers.add(packageName + ".DBInfo$Builder");
    helpers.add(packageName + ".JDBCUtils");
    helpers.add(packageName + ".JDBCMaps");
    helpers.add(packageName + ".JDBCConnectionUrlParser");

    helpers.add("io.opentelemetry.auto.decorator.BaseDecorator");
    helpers.add("io.opentelemetry.auto.decorator.ClientDecorator");
    helpers.add("io.opentelemetry.auto.decorator.DatabaseClientDecorator");
    helpers.add(packageName + ".JDBCDecorator");

    for (final JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      helpers.add(parser.getClass().getName());
    }
    return helpers.toArray(new String[0]);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArgument(0, String.class)).and(isPublic()),
        StatementInstrumentation.class.getName() + "$StatementAdvice");
  }

  public static class StatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final String sql, @Advice.This final Statement statement) {
      final Connection connection = connectionFromStatement(statement);
      if (connection == null) {
        return null;
      }

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span = TRACER.spanBuilder("database.query").setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, connection);
      DECORATE.onStatement(span, sql);
      span.setAttribute("span.origin.type", statement.getClass().getName());
      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(Statement.class);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}

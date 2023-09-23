/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractBooleanAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

class LettuceReactiveClientTest extends AbstractLettuceClientTest {
  static RedisReactiveCommands<String, String> reactiveCommands;
  static RedisCommands<String, String> syncCommands;

  @BeforeAll
  static void setUp() {
    redisServer.start();

    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    reactiveCommands = connection.reactive();
    syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set + 1 connect trace
    testing.waitForTraces(2);
    testing.clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisClient.shutdown();
    redisServer.stop();
  }

  @Test
  void testSetCommandWithSubscribeOnADefinedConsumer() {
    CompletableFuture<String> future = new CompletableFuture<>();

    Consumer<String> consumer =
        res ->
            testing.runWithSpan(
                "callback",
                () -> {
                  assertThat(res).isEqualTo("OK");
                  future.complete(res);
                });

    testing.runWithSpan(
        "parent", () -> reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer));

    await().untilAsserted(() -> assertThat(future).isCompletedWithValue("OK"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET TESTSETKEY ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testGetCommandWithLambdaFunction() {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .get("TESTKEY")
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTVAL");
              future.complete(res);
            });

    await().untilAsserted(() -> assertThat(future).isCompletedWithValue("TESTVAL"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET TESTKEY"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET"))));
  }

  @Test
  void testGetNonExistentKeyCommand() {
    CompletableFuture<String> future = new CompletableFuture<>();
    String defaultVal = "NOT THIS VALUE";

    testing.runWithSpan(
        "parent",
        () -> {
          reactiveCommands
              .get("NON_EXISTENT_KEY")
              .defaultIfEmpty(defaultVal)
              .subscribe(
                  res -> {
                    testing.runWithSpan(
                        "callback",
                        () -> {
                          assertThat(res).isEqualTo(defaultVal);
                          future.complete(res);
                        });
                  });
        });

    await().untilAsserted(() -> assertThat(future).isCompletedWithValue(defaultVal));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET NON_EXISTENT_KEY"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testCommandWithNoArguments() {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .randomkey()
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTKEY");
              future.complete(res);
            });

    await().untilAsserted(() -> assertThat(future).isCompletedWithValue("TESTKEY"));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "RANDOMKEY"),
                            equalTo(SemanticAttributes.DB_OPERATION, "RANDOMKEY"))));
  }

  @Test
  void testCommandFluxPublisher() {
    reactiveCommands.command().subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "COMMAND"),
                            equalTo(SemanticAttributes.DB_OPERATION, "COMMAND"),
                            satisfies(
                                AttributeKey.longKey("lettuce.command.results.count"),
                                val -> val.isGreaterThan(100)))));
  }

  @Test
  void testCommandCancelAfter2OnFluxPublisher() {
    reactiveCommands.command().take(2).subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "COMMAND"),
                            equalTo(SemanticAttributes.DB_OPERATION, "COMMAND"),
                            satisfies(
                                AttributeKey.booleanKey("lettuce.command.cancelled"),
                                AbstractBooleanAssert::isTrue),
                            satisfies(
                                AttributeKey.longKey("lettuce.command.results.count"),
                                val -> val.isEqualTo(2)))));
  }

  @Test
  void testNonReactiveCommandShouldNotProduceSpan() {
    String res = reactiveCommands.digest(null);

    assertThat(res).isNotNull();
    assertThat(testing.spans().size()).isEqualTo(0);
  }

  @Test
  void testDebugSegfaultCommandReturnsMonoVoidWithNoArgumentShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    RedisReactiveCommands<String, String> commands = newContainerCommands();

    commands.debugSegfault().subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEBUG")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "DEBUG SEGFAULT"),
                            equalTo(SemanticAttributes.DB_OPERATION, "DEBUG"))));
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    RedisReactiveCommands<String, String> commands = newContainerCommands();

    commands.shutdown(false).subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHUTDOWN")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SHUTDOWN NOSAVE"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SHUTDOWN"))));
  }

  @Test
  void testBlockingSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET a ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET a"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET"))));
  }

  @Test
  void testAsyncSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET a ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET a"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET"))));
  }

  @Test
  void testAsyncSubscriberWithSpecificThreadPool() {
    testing.runWithSpan(
        "test-parent",
        () ->
            reactiveCommands
                .set("a", "1")
                .then(reactiveCommands.get("a"))
                .subscribeOn(Schedulers.elastic())
                .subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET a ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET a"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET"))));
  }

  private static RedisReactiveCommands<String, String> newContainerCommands() {
    StatefulRedisConnection<String, String> statefulConnection = newContainerConnection();

    RedisReactiveCommands<String, String> commands = statefulConnection.reactive();
    // 1 connect trace
    testing.waitForTraces(1);
    testing.clearData();
    return commands;
  }
}

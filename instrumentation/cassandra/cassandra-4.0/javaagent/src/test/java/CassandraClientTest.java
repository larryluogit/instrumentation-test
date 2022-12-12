import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.stream.Stream;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_CASSANDRA_TABLE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_SOCK_PEER_ADDR;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_SOCK_PEER_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NET_SOCK_PEER_PORT;
import static org.junit.jupiter.api.Named.named;

public class CassandraClientTest {

  private static final Logger logger = LoggerFactory.getLogger(CassandraClientTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @SuppressWarnings("rawtypes")
  private static GenericContainer cassandra;
  private static int cassandraPort;

  @BeforeAll
  static void beforeAll() {
    cassandra = new GenericContainer<>("cassandra:4.0")
        .withEnv("JVM_OPTS", "-Xmx128m -Xms128m")
        .withExposedPorts(9042)
        .withLogConsumer(new Slf4jLogConsumer(logger))
        .withStartupTimeout(Duration.ofMinutes(2));
    cassandra.start();

    cassandraPort = cassandra.getMappedPort(9042);
  }

  @AfterAll
  static void afterAll() {
    cassandra.stop();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideSyncParameters")
  void syncTest(Parameter parameter) {
    CqlSession session = getSession(parameter.keyspace);

    session.execute(parameter.statement);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(parameter.spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfying(
                            attributes ->
                                assertThat(attributes)
                                    .containsEntry(NET_SOCK_PEER_ADDR, "127.0.0.1")
                                    .containsEntry(NET_SOCK_PEER_NAME, "localhost")
                                    .containsEntry(NET_SOCK_PEER_PORT, cassandraPort)
                                    .containsEntry(DB_SYSTEM, "cassandra")
                                    .containsEntry(DB_STATEMENT, parameter.expectedStatement)
                                    .containsEntry(DB_CASSANDRA_CONSISTENCY_LEVEL, "LOCAL_ONE")
                                    .containsEntry(DB_CASSANDRA_COORDINATOR_DC, "datacenter1")
                                    .hasEntrySatisfying(DB_CASSANDRA_COORDINATOR_ID, val -> assertThat(val).isInstanceOf(String.class))
                                    .hasEntrySatisfying(DB_CASSANDRA_IDEMPOTENCE, val -> assertThat(val).isInstanceOf(Boolean.class))
                                    .containsEntry(DB_CASSANDRA_PAGE_SIZE, 5000)
                                    .containsEntry(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, 0)
                        )
                        .hasAttributesSatisfying(
                            attributes -> {
                              assertThat(attributes.get(DB_OPERATION))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.operation)
                                  );
                              assertThat(attributes.get(DB_NAME))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.keyspace)
                                  );
                              assertThat(attributes.get(DB_CASSANDRA_TABLE))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.table)
                                  );
                            }
                        )
            )
    );

    session.close();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAsyncParameters")
  void asyncTest(Parameter parameter) throws Exception {
    CqlSession session = getSession(parameter.keyspace);

    testing.runWithSpan("parent",
        () -> session.executeAsync(parameter.statement).toCompletableFuture().whenComplete(
            (result, throwable) -> testing.runWithSpan("child", () -> {})
        ).get()
    );

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent(),
                span ->
                    span.hasName(parameter.spanName)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfying(
                            attributes ->
                                assertThat(attributes)
                                    .containsEntry(NET_SOCK_PEER_ADDR, "127.0.0.1")
                                    .containsEntry(NET_SOCK_PEER_NAME, "localhost")
                                    .containsEntry(NET_SOCK_PEER_PORT, cassandraPort)
                                    .containsEntry(DB_SYSTEM, "cassandra")
                                    .containsEntry(DB_STATEMENT, parameter.expectedStatement)
                                    .containsEntry(DB_CASSANDRA_CONSISTENCY_LEVEL, "LOCAL_ONE")
                                    .containsEntry(DB_CASSANDRA_COORDINATOR_DC, "datacenter1")
                                    .hasEntrySatisfying(DB_CASSANDRA_COORDINATOR_ID, val -> assertThat(val).isInstanceOf(String.class))
                                    .hasEntrySatisfying(DB_CASSANDRA_IDEMPOTENCE, val -> assertThat(val).isInstanceOf(Boolean.class))
                                    .containsEntry(DB_CASSANDRA_PAGE_SIZE, 5000)
                                    .containsEntry(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, 0)
                        )
                        .hasAttributesSatisfying(
                            attributes -> {
                              assertThat(attributes.get(DB_OPERATION))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.operation)
                                  );
                              assertThat(attributes.get(DB_NAME))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.keyspace)
                                  );
                              assertThat(attributes.get(DB_CASSANDRA_TABLE))
                                  .satisfiesAnyOf(
                                      v -> assertThat(v).isNull(),
                                      v -> assertThat(v).isEqualTo(parameter.table)
                                  );
                            }
                        ),
                span ->
                    span.hasName("child")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
            )
    );

    session.close();
  }

  private static Stream<Arguments> provideSyncParameters() {
    return Stream.of(
        Arguments.of(named("Drop keyspace if exists", new Parameter(null, "DROP KEYSPACE IF EXISTS sync_test", "DROP KEYSPACE IF EXISTS sync_test", "DB Query", null, null))),
        Arguments.of(named("Create keyspace with replication", new Parameter(null, "CREATE KEYSPACE sync_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}", "CREATE KEYSPACE sync_test WITH REPLICATION = {?:?, ?:?}", "DB Query", null, null))),
        Arguments.of(named("Create table", new Parameter("sync_test", "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )", "CREATE TABLE sync_test.users ( id UUID PRIMARY KEY, name text )", "sync_test", null, null))),
        Arguments.of(named("Insert data", new Parameter("sync_test", "INSERT INTO sync_test.users (id, name) values (uuid(), 'alice')", "INSERT INTO sync_test.users (id, name) values (uuid(), ?)", "INSERT sync_test.users", "INSERT", "sync_test.users"))),
        Arguments.of(named("Select data", new Parameter("sync_test", "SELECT * FROM users where name = 'alice' ALLOW FILTERING", "SELECT * FROM users where name = ? ALLOW FILTERING", "SELECT sync_test.users", "SELECT", "users")))
    );
  }

  private static Stream<Arguments> provideAsyncParameters() {
    return Stream.of(
        Arguments.of(named("Drop keyspace if exists", new Parameter(null, "DROP KEYSPACE IF EXISTS async_test", "DROP KEYSPACE IF EXISTS async_test", "DB Query", null, null))),
        Arguments.of(named("Create keyspace with replication", new Parameter(null, "CREATE KEYSPACE async_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}", "CREATE KEYSPACE async_test WITH REPLICATION = {?:?, ?:?}", "DB Query", null, null))),
        Arguments.of(named("Create table", new Parameter("async_test", "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )", "CREATE TABLE async_test.users ( id UUID PRIMARY KEY, name text )", "async_test", null, null))),
        Arguments.of(named("Insert data", new Parameter("async_test", "INSERT INTO async_test.users (id, name) values (uuid(), 'alice')", "INSERT INTO async_test.users (id, name) values (uuid(), ?)", "INSERT async_test.users", "INSERT", "async_test.users"))),
        Arguments.of(named("Select data", new Parameter("async_test", "SELECT * FROM users where name = 'alice' ALLOW FILTERING", "SELECT * FROM users where name = ? ALLOW FILTERING", "SELECT async_test.users", "SELECT", "users")))
    );
  }

  private static class Parameter {
    public final String keyspace;
    public final String statement;
    public final String expectedStatement;
    public final String spanName;
    public final String operation;
    public final String table;

    public Parameter(String keyspace, String statement, String expectedStatement, String spanName,
        String operation, String table) {
      this.keyspace = keyspace;
      this.statement = statement;
      this.expectedStatement = expectedStatement;
      this.spanName = spanName;
      this.operation = operation;
      this.table = table;
    }
  }

  CqlSession getSession(String keyspace) {
    DriverConfigLoader configLoader = DefaultDriverConfigLoader.builder()
        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(0))
        .build();
    return CqlSession.builder()
        .addContactPoint(new InetSocketAddress("localhost", cassandraPort))
        .withConfigLoader(configLoader)
        .withLocalDatacenter("datacenter1")
        .withKeyspace(keyspace)
        .build();
  }
}

/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.Arrays;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private MongoClient client;

  @BeforeAll
  void setupSpec() {
    client =
        MongoClients.create(
            MongoClientSettings.builder()
                .applyToClusterSettings(
                    builder ->
                        builder
                            .hosts(singletonList(new ServerAddress(host, port)))
                            .description("some-description"))
                .build());
  }

  @AfterAll
  void cleanupSpec() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://" + host + ":" + port).getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionWithAlreadyBuiltClientOptions(
      String dbName, String collectionName) {
    MongoClientSettings clientSettings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder ->
                    builder
                        .hosts(singletonList(new ServerAddress(host, port)))
                        .description("some-description"))
            .build();
    MongoClientSettings newClientSettings = MongoClientSettings.builder(clientSettings).build();
    MongoDatabase db = MongoClients.create(newClientSettings).getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    MongoClientSettings.Builder clientSettings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder ->
                    builder
                        .hosts(singletonList(new ServerAddress(host, port)))
                        .description("some-description"));
    clientSettings.build();
    MongoDatabase db = MongoClients.create(clientSettings.build()).getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  protected int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    return (int) db.getCollection(collectionName).estimatedDocumentCount();
  }

  @Override
  protected MongoCollection<Document> setupInsert(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected int insert(MongoCollection<Document> collection) {
    collection.insertOne(new Document("password", "SECRET"));
    return (int) collection.estimatedDocumentCount();
  }

  @Override
  protected MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertOne(new Document("password", "OLDPW"));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected int update(MongoCollection<Document> collection) {
    UpdateResult result =
        collection.updateOne(
            new BsonDocument("password", new BsonString("OLDPW")),
            new BsonDocument("$set", new BsonDocument("password", new BsonString("NEWPW"))));
    collection.estimatedDocumentCount();
    return (int) result.getModifiedCount();
  }

  @Override
  protected MongoCollection<Document> setupDelete(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertOne(new Document("password", "SECRET"));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected int delete(MongoCollection<Document> collection) {
    DeleteResult result =
        collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")));
    collection.estimatedDocumentCount();
    return (int) result.getDeletedCount();
  }

  @Override
  protected MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertMany(
                      Arrays.asList(
                          new Document("_id", 0), new Document("_id", 1), new Document("_id", 2)));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  protected void getMore(MongoCollection<Document> collection) {
    collection
        .find()
        .filter(new Document("_id", new Document("$gte", 0)))
        .batchSize(2)
        .into(new ArrayList<>());
  }

  @Override
  protected void error(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  db.createCollection(collectionName);
                  return db.getCollection(collectionName);
                });
    ignoreTracesAndClear(1);
    collection.updateOne(new BsonDocument(), new BsonDocument());
  }

  @Test
  @DisplayName("test client failure")
  void testClientFailure() {
    String dbName = "test_db";
    String collectionName = createCollectionName();

    MongoClient client =
        MongoClients.create("mongodb://" + host + ":" + UNUSABLE_PORT + "/?connectTimeoutMS=10");

    assertThatThrownBy(
            () -> {
              MongoDatabase db = client.getDatabase(dbName);
              db.createCollection(collectionName);
            })
        .isInstanceOf(MongoTimeoutException.class);
    // Unfortunately not caught by our instrumentation.
    testing().waitAndAssertTraces();
  }
}

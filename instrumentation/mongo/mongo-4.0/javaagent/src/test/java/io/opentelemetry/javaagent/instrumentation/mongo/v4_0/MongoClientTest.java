/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.mongodb.MongoClientSettings;
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
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opentest4j.TestAbortedException;

class MongoClientTest extends AbstractMongoClientTest<MongoCollection<Document>> {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private MongoClient client;

  @BeforeAll
  public void setup() {
    client = MongoClients.create("mongodb://" + host + ":" + port);
  }

  @AfterAll
  public void cleanup() {
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
  public void createCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  public void createCollectionNoDescription(String dbName, String collectionName) {
    MongoDatabase db = MongoClients.create("mongodb://" + host + ":" + port).getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  public void createCollectionWithAlreadyBuiltClientOptions(String dbName, String collectionName) {
    throw new TestAbortedException("not tested on 4.0");
  }

  @Override
  public void createCollectionCallingBuildTwice(String dbName, String collectionName) {
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder -> builder.hosts(singletonList(new ServerAddress(host, port))))
            .build();
    MongoDatabase db = MongoClients.create(settings).getDatabase(dbName);
    db.createCollection(collectionName);
  }

  @Override
  public int getCollection(String dbName, String collectionName) {
    MongoDatabase db = client.getDatabase(dbName);
    return (int) db.getCollection(collectionName).estimatedDocumentCount();
  }

  @Override
  public MongoCollection<Document> setupInsert(String dbName, String collectionName) {
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
  public int insert(MongoCollection<Document> collection) {
    collection.insertOne(new Document("password", "SECRET"));
    return (int) collection.estimatedDocumentCount();
  }

  @Override
  public MongoCollection<Document> setupUpdate(String dbName, String collectionName) {
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
  public int update(MongoCollection<Document> collection) {
    UpdateResult result =
        collection.updateOne(
            new BsonDocument("password", new BsonString("OLDPW")),
            new BsonDocument("$set", new BsonDocument("password", new BsonString("NEWPW"))));
    collection.estimatedDocumentCount();
    return (int) result.getModifiedCount();
  }

  @Override
  public MongoCollection<Document> setupDelete(String dbName, String collectionName) {
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
  public int delete(MongoCollection<Document> collection) {
    DeleteResult result =
        collection.deleteOne(new BsonDocument("password", new BsonString("SECRET")));
    collection.estimatedDocumentCount();
    return (int) result.getDeletedCount();
  }

  @Override
  public MongoCollection<Document> setupGetMore(String dbName, String collectionName) {
    MongoCollection<Document> collection =
        testing()
            .runWithSpan(
                "setup",
                () -> {
                  MongoDatabase db = client.getDatabase(dbName);
                  MongoCollection<Document> coll = db.getCollection(collectionName);
                  coll.insertMany(
                      asList(
                          new Document("_id", 0), new Document("_id", 1), new Document("_id", 2)));
                  return coll;
                });
    ignoreTracesAndClear(1);
    return collection;
  }

  @Override
  public void getMore(MongoCollection<Document> collection) {
    collection
        .find()
        .filter(new Document("_id", new Document("$gte", 0)))
        .batchSize(2)
        .into(new ArrayList<>());
  }

  @Override
  public void error(String dbName, String collectionName) {
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
}

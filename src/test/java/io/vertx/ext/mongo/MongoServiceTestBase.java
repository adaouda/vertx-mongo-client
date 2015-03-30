/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.mongo;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.vertx.ext.mongo.WriteOption.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class MongoServiceTestBase extends VertxTestBase {

  private static MongodExecutable exe;

  protected static String getConnectionString() {
    return getProperty("connection_string");
  }

  protected static String getDatabaseName() {
    return getProperty("db_name");
  }

  protected static String getProperty(String name) {
    String s = System.getProperty(name);
    if (s != null) {
      s = s.trim();
      if (s.length() > 0) {
        return s;
      }
    }

    return null;
  }

  @BeforeClass
  public static void startMongo() throws Exception {
    if (getConnectionString() == null ) {
      IMongodConfig config = new MongodConfigBuilder().
        version(Version.Main.PRODUCTION).
        net(new Net(27018, Network.localhostIsIPv6())).
        build();
      exe = MongodStarter.getDefaultInstance().prepare(config);
      exe.start();
    }
  }

  @AfterClass
  public static void stopMongo() {
    if (exe != null) {
      exe.stop();
    }
  }


  protected MongoService mongoService;

  private String randomCollection() {
    return "ext-mongo" + TestUtils.randomAlphaString(20);
  }

  protected JsonObject getConfig() {
    JsonObject config = new JsonObject();
    String connectionString = getConnectionString();
    if (connectionString != null) {
      config.put("connection_string", connectionString);
    } else {
      config.put("connection_string", "mongodb://localhost:27018");
    }
    String databaseName = getDatabaseName();
    if (databaseName != null) {
      config.put("db_name", databaseName);
    }
    return config;
  }

  protected List<String> getOurCollections(List<String> colls) {
    List<String> ours = new ArrayList<>();
    for (String coll : colls) {
      if (coll.startsWith("ext-mongo")) {
        ours.add(coll);
      }
    }
    return ours;
  }

  protected void dropCollections(CountDownLatch latch) {
    // Drop all the collections in the db
    mongoService.getCollections(onSuccess(list -> {
      AtomicInteger collCount = new AtomicInteger();
      List<String> toDrop = getOurCollections(list);
      int count = toDrop.size();
      if (!toDrop.isEmpty()) {
        for (String collection : toDrop) {
          mongoService.dropCollection(collection, onSuccess(v -> {
            if (collCount.incrementAndGet() == count) {
              latch.countDown();
            }
          }));
        }
      } else {
        latch.countDown();
      }
    }));
  }

  @Test
  public void testCreateAndGetCollection() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      mongoService.getCollections(onSuccess(list -> {
        List<String> ours = getOurCollections(list);
        assertEquals(1, ours.size());
        assertEquals(collection, ours.get(0));
        String collection2 = randomCollection();
        mongoService.createCollection(collection2, onSuccess(res2 -> {
          mongoService.getCollections(onSuccess(list2 -> {
            List<String> ours2 = getOurCollections(list2);
            assertEquals(2, ours2.size());
            assertTrue(ours2.contains(collection));
            assertTrue(ours2.contains(collection2));
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCreateCollectionAlreadyExists() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      mongoService.createCollection(collection, onFailure(ex -> {
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testDropCollection() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      mongoService.dropCollection(collection, onSuccess(res2 -> {
        mongoService.getCollections(onSuccess(list -> {
          List<String> ours = getOurCollections(list);
          assertTrue(ours.isEmpty());
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRunCommand() throws Exception {
    JsonObject ping = new JsonObject().put("isMaster", 1);
    mongoService.runCommand(ping, onSuccess(reply -> {
      assertTrue(reply.getBoolean("ismaster"));
      testComplete();
    }));
    await();
  }

  @Test
  public void testRunInvalidCommand() throws Exception {
    JsonObject ping = new JsonObject().put("iuhioqwdqhwd", 1);
    mongoService.runCommand(ping, onFailure(ex -> {
      testComplete();
    }));
    await();
  }

  @Test
  public void testInsertNoCollection() {
    String collection = randomCollection();
    String random = TestUtils.randomAlphaString(20);
    mongoService.insert(collection, new JsonObject().put("foo", random), onSuccess(id -> {
      assertNotNull(id);
      mongoService.find(collection, new JsonObject(), onSuccess(docs -> {
        assertNotNull(docs);
        assertEquals(1, docs.size());
        assertEquals(random, docs.get(0).getString("foo"));
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testInsertNoPreexistingID() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testInsertPreexistingID() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      String genID  = TestUtils.randomAlphaString(100);
      doc.put("_id", genID);
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNull(id);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testInsertAlreadyExists() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        doc.put("_id", id);
        mongoService.insert(collection, doc, onFailure(t -> {
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testInsertWithOptions() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.insertWithOptions(collection, doc, UNACKNOWLEDGED, onSuccess(id -> {
        assertNotNull(id);
        testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testInsertWithNestedListMap() throws Exception {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("foo", "bar");
    map.put("nestedMap", nestedMap);
    map.put("nestedList", Arrays.asList(1, 2, 3));

    String collection = randomCollection();
    JsonObject doc = new JsonObject(map);
    mongoService.insert(collection, doc, onSuccess(id -> {
      assertNotNull(id);
      mongoService.findOneWithFields(collection, new JsonObject().put("_id", id), null, onSuccess(result -> {
          assertNotNull(result);
          assertNotNull(result.getJsonObject("nestedMap"));
          assertEquals("bar", result.getJsonObject("nestedMap").getString("foo"));
          assertNotNull(result.getJsonArray("nestedList"));
          assertEquals(1, (int) result.getJsonArray("nestedList").getInteger(0));
          assertEquals(2, (int) result.getJsonArray("nestedList").getInteger(1));
          assertEquals(3, (int) result.getJsonArray("nestedList").getInteger(2));
          testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testSave() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.save(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        doc.put("_id", id);
        doc.put("newField", "sheep");
        // Save again - it should update
        mongoService.save(collection, doc, onSuccess(id2 -> {
          assertNull(id2);
          mongoService.findOneWithFields(collection, new JsonObject(), null, onSuccess(res2 -> {
              assertEquals("sheep", res2.getString("newField"));
              testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testSaveWithNestedListMap() throws Exception {
    Map<String, Object> map = new HashMap<>();
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("foo", "bar");
    map.put("nestedMap", nestedMap);
    map.put("nestedList", Arrays.asList(1, 2, 3));

    String collection = randomCollection();
    JsonObject doc = new JsonObject(map);
    mongoService.save(collection, doc, onSuccess(id -> {
      assertNotNull(id);
      mongoService.findOneWithFields(collection, new JsonObject().put("_id", id), null, onSuccess(result -> {
          assertNotNull(result);
          assertNotNull(result.getJsonObject("nestedMap"));
          assertEquals("bar", result.getJsonObject("nestedMap").getString("foo"));
          assertNotNull(result.getJsonArray("nestedList"));
          assertEquals(1, (int) result.getJsonArray("nestedList").getInteger(0));
          assertEquals(2, (int) result.getJsonArray("nestedList").getInteger(1));
          assertEquals(3, (int) result.getJsonArray("nestedList").getInteger(2));
          testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testSaveWithOptions() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.saveWithOptions(collection, doc, ACKNOWLEDGED, onSuccess(id -> {
        assertNotNull(id);
        doc.put("_id", id);
        doc.put("newField", "sheep");
        // Save again - it should update
        mongoService.save(collection, doc, onSuccess(id2 -> {
          assertNull(id2);
          mongoService.findOneWithFields(collection, new JsonObject(), null, onSuccess(res2 -> {
              assertEquals("sheep", res2.getString("newField"));
              testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testCountNoCollection() {
    String collection = randomCollection();
    mongoService.count(collection, new JsonObject(), onSuccess(count -> {
      assertEquals((long) 0, (long) count);
      testComplete();
    }));

    await();
  }

  @Test
  public void testCount() throws Exception {
    int num = 10;
    String collection = randomCollection();
    insertDocs(collection, num, onSuccess(res -> {
      mongoService.count(collection, new JsonObject(), onSuccess(count -> {
        assertNotNull(count);
        assertEquals(num, count.intValue());
        testComplete();
      }));
    }));

    await();
  }

  @Test
  public void testCountWithQuery() throws Exception {
    int num = 10;
    String collection = randomCollection();
    CountDownLatch latch = new CountDownLatch(num);
    for (int i = 0; i < num; i++) {
      JsonObject doc = createDoc();
      if (i % 2 == 0) {
        doc.put("flag", true);
      }
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        latch.countDown();
      }));
    }

    awaitLatch(latch);

    JsonObject query = new JsonObject().put("flag", true);
    mongoService.count(collection, query, onSuccess(count -> {
      assertNotNull(count);
      assertEquals(num / 2, count.intValue());
      testComplete();
    }));

    await();
  }

  @Test
  public void testFindOne() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject orig = createDoc();
      JsonObject doc = orig.copy();
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        mongoService.findOneWithFields(collection, new JsonObject().put("foo", "bar"), null, onSuccess(obj -> {
            assertTrue(obj.containsKey("_id"));
            obj.remove("_id");
            assertEquals(orig, obj);
            testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testFindOneWithKeys() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      JsonObject doc = createDoc();
      mongoService.insert(collection, doc, onSuccess(id -> {
        assertNotNull(id);
        mongoService.findOneWithFields(collection, new JsonObject().put("foo", "bar"), new JsonObject().put("num", true), onSuccess(obj -> {
            assertEquals(2, obj.size());
            assertEquals(123, obj.getInteger("num").intValue());
            assertTrue(obj.containsKey("_id"));
            testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testFindOneNotFound() throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      mongoService.findOneWithFields(collection, new JsonObject().put("foo", "bar"), null, onSuccess(obj -> {
          assertNull(obj);
          testComplete();
      }));
    }));
    await();
  }

  @Test
  public void testFind() throws Exception {
    int num = 10;
    doTestFind(num, new JsonObject(), new FindOptions(), results -> {
      assertEquals(num, results.size());
      for (JsonObject doc: results) {
        assertEquals(6, doc.size()); // Contains _id too
      }
    });
  }

  @Test
  public void testFindWithFields() throws Exception {
    int num = 10;
    doTestFind(num, new JsonObject(), new FindOptions().setFields(new JsonObject().put("num", true)), results -> {
      assertEquals(num, results.size());
      for (JsonObject doc: results) {
        assertEquals(2, doc.size()); // Contains _id too
      }
    });
  }

  @Test
  public void testFindWithSort() throws Exception {
    int num = 11;
    doTestFind(num, new JsonObject(), new FindOptions().setSort(new JsonObject().put("foo", 1)), results -> {
      assertEquals(num, results.size());
      assertEquals("bar0", results.get(0).getString("foo"));
      assertEquals("bar1", results.get(1).getString("foo"));
      assertEquals("bar10", results.get(2).getString("foo"));
    });
  }

  @Test
  public void testFindWithLimit() throws Exception {
    int num = 10;
    int limit = 3;
    doTestFind(num, new JsonObject(), new FindOptions().setLimit(limit), results -> {
      assertEquals(limit, results.size());
    });
  }

  @Test
  public void testFindWithLimitLarger() throws Exception {
    int num = 10;
    int limit = 20;
    doTestFind(num, new JsonObject(), new FindOptions().setLimit(limit), results -> {
      assertEquals(num, results.size());
    });
  }

  @Test
  public void testFindWithSkip() throws Exception {
    int num = 10;
    int skip = 3;
    doTestFind(num, new JsonObject(), new FindOptions().setSkip(skip), results -> {
      assertEquals(num - skip, results.size());
    });
  }

  @Test
  public void testFindWithSkipLarger() throws Exception {
    int num = 10;
    int skip = 20;
    doTestFind(num, new JsonObject(), new FindOptions().setSkip(skip), results -> {
      assertEquals(0, results.size());
    });
  }

  private void doTestFind(int numDocs, JsonObject query, FindOptions options, Consumer<List<JsonObject>> resultConsumer) throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      insertDocs(collection, numDocs, onSuccess(res2 -> {
        mongoService.findWithOptions(collection, query, options, onSuccess(res3 -> {
          resultConsumer.accept(res3);
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testReplace() {
    String collection = randomCollection();
    JsonObject doc = createDoc();
    mongoService.insert(collection, doc, onSuccess(id -> {
      assertNotNull(id);
      JsonObject replacement = createDoc();
      replacement.put("replacement", true);
      mongoService.replace(collection, new JsonObject().put("_id", id), replacement, onSuccess(v -> {
        mongoService.find(collection, new JsonObject(), onSuccess(list -> {
          assertNotNull(list);
          assertEquals(1, list.size());
          JsonObject result = list.get(0);
          assertEquals(id, result.getString("_id"));
          result.remove("_id");
          replacement.remove("_id"); // id won't be there for event bus
          assertEquals(replacement, result);
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testReplaceUpsert() {
    String collection = randomCollection();
    JsonObject doc = createDoc();
    mongoService.insert(collection, doc, onSuccess(id -> {
      assertNotNull(id);
      JsonObject replacement = createDoc();
      replacement.put("replacement", true);
      mongoService.replaceWithOptions(collection, new JsonObject().put("_id", "foo"), replacement, new UpdateOptions(true), onSuccess(v -> {
        mongoService.find(collection, new JsonObject(), onSuccess(list -> {
          assertNotNull(list);
          assertEquals(2, list.size());
          JsonObject result = null;
          for (JsonObject o : list) {
            if (o.containsKey("replacement")) {
              result = o;
            }
          }
          assertNotNull(result);
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testReplaceUpsert2() {
    String collection = randomCollection();
    JsonObject doc = createDoc();
    mongoService.insert(collection, doc, onSuccess(id -> {
      assertNotNull(id);
      JsonObject replacement = createDoc();
      replacement.put("replacement", true);
      mongoService.replaceWithOptions(collection, new JsonObject().put("_id", id), replacement, new UpdateOptions(true), onSuccess(v -> {
        mongoService.find(collection, new JsonObject(), onSuccess(list -> {
          assertNotNull(list);
          assertEquals(1, list.size());
          assertEquals(id, list.get(0).getString("_id"));
          testComplete();
        }));
      }));
    }));

    await();
  }

  @Test
  public void testUpdate() throws Exception {
    String collection = randomCollection();
    mongoService.insert(collection, createDoc(), onSuccess(id -> {
      mongoService.update(collection, new JsonObject().put("_id", id), new JsonObject().put("$set", new JsonObject().put("foo", "fooed")), onSuccess(res -> {
        mongoService.findOneWithFields(collection, new JsonObject().put("_id", id), null, onSuccess(doc -> {
            System.out.println(doc.getString("foo"));
            assertEquals("fooed", doc.getString("foo"));
            testComplete();
        }));
      }));
    }));
  }

  @Test
  public void testUpdateOne() throws Exception {
    int num = 1;
    doTestUpdate(num, new JsonObject().put("num", 123), new JsonObject().put("$set", new JsonObject().put("foo", "fooed")), new UpdateOptions(), results -> {
      assertEquals(num, results.size());
      for (JsonObject doc : results) {
        assertEquals(6, doc.size());
        assertEquals("fooed", doc.getString("foo"));
        assertNotNull(doc.getString("_id"));
      }
    });
  }

  @Test
  public void testUpdateAll() throws Exception {
    int num = 10;
    doTestUpdate(num, new JsonObject().put("num", 123), new JsonObject().put("$set", new JsonObject().put("foo", "fooed")), new UpdateOptions(false, true), results -> {
      assertEquals(num, results.size());
      for (JsonObject doc : results) {
        assertEquals(6, doc.size());
        assertEquals("fooed", doc.getString("foo"));
        assertNotNull(doc.getString("_id"));
      }
    });
  }

  private void doTestUpdate(int numDocs, JsonObject query, JsonObject update, UpdateOptions options,
                            Consumer<List<JsonObject>> resultConsumer) throws Exception {
    String collection = randomCollection();
    mongoService.createCollection(collection, onSuccess(res -> {
      insertDocs(collection, numDocs, onSuccess(res2 -> {
        mongoService.updateWithOptions(collection, query, update, options, onSuccess(res3 -> {
          mongoService.find(collection, new JsonObject(), onSuccess(res4 -> {
            resultConsumer.accept(res4);
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRemoveOne() throws Exception {
    String collection = randomCollection();
    insertDocs(collection, 6, onSuccess(res2 -> {
      mongoService.removeOne(collection, new JsonObject().put("num", 123), onSuccess(res3 -> {
        mongoService.count(collection, new JsonObject(), onSuccess(count -> {
          assertEquals(5, (long) count);
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRemoveOneWithOptions() throws Exception {
    String collection = randomCollection();
    insertDocs(collection, 6, onSuccess(res2 -> {
      mongoService.removeOneWithOptions(collection, new JsonObject().put("num", 123), UNACKNOWLEDGED, onSuccess(res3 -> {
        mongoService.count(collection, new JsonObject(), onSuccess(count -> {
          assertEquals(5, (long) count);
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRemoveMultiple() throws Exception {
    String collection = randomCollection();
    insertDocs(collection, 10, onSuccess(v -> {
      mongoService.remove(collection, new JsonObject(), onSuccess(v2 -> {
        mongoService.find(collection, new JsonObject(), onSuccess(res2 -> {
          assertTrue(res2.isEmpty());
          testComplete();
        }));
      }));
    }));
    await();
  }

  @Test
  public void testRemoveWithOptions() throws Exception {
    String collection = randomCollection();
    insertDocs(collection, 10, onSuccess(v -> {
      mongoService.removeWithOptions(collection, new JsonObject(), ACKNOWLEDGED, onSuccess(v2 -> {
        mongoService.find(collection, new JsonObject(), onSuccess(res2 -> {
          assertTrue(res2.isEmpty());
          testComplete();
        }));
      }));
    }));
    await();
  }

  private JsonObject createDoc() {
    return new JsonObject().put("foo", "bar").put("num", 123).put("big", true).put("date", new JsonObject().put("$date", 100100L)).
      put("other", new JsonObject().put("quux", "flib").put("myarr",
        new JsonArray().add("blah").add(true).add(312)));
  }

  private JsonObject createDoc(int num) {
    return new JsonObject().put("foo", "bar" + (num != -1 ? num : "")).put("num", 123).put("big", true).put("date", new JsonObject().put("$date", 100100L)).
      put("other", new JsonObject().put("quux", "flib").put("myarr",
        new JsonArray().add("blah").add(true).add(312)));
  }

  private void insertDocs(String collection, int num, Handler<AsyncResult<Void>> resultHandler) {
    if (num != 0) {
      AtomicInteger cnt = new AtomicInteger();
      for (int i = 0; i < num; i++) {
        JsonObject doc = createDoc(i);
        mongoService.insert(collection, doc, ar -> {
          if (ar.succeeded()) {
            if (cnt.incrementAndGet() == num) {
              resultHandler.handle(Future.succeededFuture());
            }
          } else {
            resultHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
      }
    } else {
      resultHandler.handle(Future.succeededFuture());
    }
  }
}

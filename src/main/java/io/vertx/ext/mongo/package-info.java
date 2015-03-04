/**
 * = Vert.x MongoDB Service
 *
 * A Vert.x service allowing applications to seamlessly interact with a MongoDB instance, whether that's
 * saving, retrieving, searching, or deleting documents. Mongo is a great match for persisting data in a Vert.x application
 * since it natively handles JSON (BSON) documents.
 *
 * *Features*
 *
 * * Completely non-blocking
 * * Custom codec to support fast serialization to/from Vert.x JSON
 * * Supports a majority of the configuration options from the MongoDB Java Driver
 *
 * NOTE: The MongoDB Java Driver is still under heavy development.
 *
 * == Setting up the service
 *
 * As with other services you can use the service either by deploying it as a verticle somewhere on your network and
 * interacting with it over the event bus, either directly by sending messages, or using a service proxy.
 *
 * Please consult the services and service proxy information on how to deploy and interact with services.
 *
 * Somewhere you deploy it:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example0_1}
 * ----
 *
 * Now you can either send messages to it directly over the event bus like this:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example0_1_1}
 * ----
 *
 * or you can create a proxy to the service from wherever you are and just use that:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example0_2}
 * ----
 *
 * Alternatively you can create an instance of the service directly and just use that locally:
 *
 * [source,java]
 * ----
 * {@link examples.Examples#example0_3}
 * ----
 *
 * If you create an instance this way you should make sure you start it with {@link io.vertx.ext.mongo.MongoService#start}
 * before you use it.
 *
 * However you do it, once you've got your service you can start using it.
 *
 * == Using the API
 *
 * The service API is represented by {@link io.vertx.ext.mongo.MongoService}.
 *
 * === Saving documents
 *
 * To save a document you use {@link io.vertx.ext.mongo.MongoService#save}.
 *
 * If the document has no `\_id` field, it is inserted, otherwise, it is _upserted_. Upserted means it is inserted
 * if it doesn't already exist, otherwise it is updated.
 *
 * If the document is inserted and has no id, then the id field generated will be returned to the result handler.
 *
 * Here's an example of saving a document and getting the id back
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example1}
 * ----
 *
 * And here's an example of saving a document which already has an id.
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example2}
 * ----
 *
 * === Inserting documents
 *
 * To insert a document you use {@link io.vertx.ext.mongo.MongoService#insert}.
 *
 * If the document is inserted and has no id, then the id field generated will be returned to the result handler.
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example3}
 * ----
 *
 * If a document is inserted with an id, and a document with that id already eists, the insert will fail:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example4}
 * ----
 *
 * === Updating documents
 *
 * To update a documents you use {@link io.vertx.ext.mongo.MongoService#update}.
 *
 * This updates one or multiple documents in a collection. The json object that is passed in the `update`
 * parameter must contain http://docs.mongodb.org/manual/reference/operator/update-field/[Update Operators] and determines
 * how the object is updated.
 *
 * The json object specified in the query parameter determines which documents in the collection will be updated.
 *
 * Here's an example of updating a document in the books collection:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example5}
 * ----
 *
 * To specify if the update should upsert or update multiple documents, use {@link io.vertx.ext.mongo.MongoService#updateWithOptions}
 * and pass in an instance of {@link io.vertx.ext.mongo.UpdateOptions}.
 *
 * This has the following fields:
 *
 * `multi`:: set to true to update multiple documents
 * `upsert`:: set to true to insert the document if the query doesn't match
 * `writeConcern`:: the write concern for this operation
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example6}
 * ----
 *
 * === Replacing documents
 *
 * To replace documents you use {@link io.vertx.ext.mongo.MongoService#replace}.
 *
 * This is similar to the update operation, however it does not take any update operators like `update`.
 * Instead it replaces the entire document with the one provided.
 *
 * Here's an example of replacing a document in the books collection
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example7}
 * ----
 *
 * === Finding documents
 *
 * To find documents you use {@link io.vertx.ext.mongo.MongoService#find}.
 *
 * The `query` parameter is used to match the documents in the collection.
 *
 * Here's a simple example with an empty query that will match all books:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example8}
 * ----
 *
 * Here's another example that will match all books by Tolkien:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example9}
 * ----
 *
 * The matching documents are returned as a list of json objects in the result handler.
 *
 * To specify things like what fields to return, how many results to return, etc use {@link io.vertx.ext.mongo.MongoService#findWithOptions}
 * and pass in the an instance of {@link io.vertx.ext.mongo.FindOptions}.
 *
 * This has the following fields:
 *
 * `fields`:: The fields to return in the results. Defaults to `null`, meaning all fields will be returned
 * `sort`:: The fields to sort by. Defaults to `null`.
 * `limit`:: The limit of the number of results to return. Default to `-1`, meaning all results will be returned.
 * `skip`:: The number of documents to skip before returning the results. Defaults to `0`.
 *
 * === Finding a single document
 *
 * To find a single document you use {@link io.vertx.ext.mongo.MongoService#findOne}.
 *
 * This works just like {@link io.vertx.ext.mongo.MongoService#find} but it returns just the first matching document.
 *
 * === Removing documents
 *
 * To remove documents use {@link io.vertx.ext.mongo.MongoService#remove}.
 *
 * The `query` parameter is used to match the documents in the collection to determine which ones to remove.
 *
 * Here's an example of removing all Tolkien books:
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example10}
 * ----
 *
 * === Removing a single document
 *
 * To remove a single document you use {@link io.vertx.ext.mongo.MongoService#removeOne}.
 *
 * This works just like {@link io.vertx.ext.mongo.MongoService#remove} but it removes just the first matching document.
 *
 * === Counting documents
 *
 * To count documents use {@link io.vertx.ext.mongo.MongoService#count}.
 *
 * Here's an example that counts the number of Tolkien books. The number is passed to the result handler.
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example11}
 * ----
 *
 * === Managing MongoDB collections
 *
 * All MongoDB documents are stored in collections.
 *
 * To get a list of all collections you can use {@link io.vertx.ext.mongo.MongoService#getCollections}
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example11_1}
 * ----
 *
 * To create a new collection you can use {@link io.vertx.ext.mongo.MongoService#createCollection}
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example11_2}
 * ----
 *
 * To drop a collection you can use {@link io.vertx.ext.mongo.MongoService#dropCollection}
 *
 * NOTE: Dropping a collection will delete all documents within it!
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example11_3}
 * ----
 *
 *
 * === Running other MongoDB commands
 *
 * You can run arbitrary MongoDB commands with {@link io.vertx.ext.mongo.MongoService#runCommand}.
 *
 * Commands can be used to run more advanced mongoDB features, such as using MapReduce.
 * For more information see the mongo docs for supported http://docs.mongodb.org/manual/reference/command[Commands].
 *
 * Here's an example of running a ping command
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example12}
 * ----
 *
 * === MongoDB Extended JSON support
 *
 * For now, only date type is supported (cf http://docs.mongodb.org/manual/reference/mongodb-extended-json )
 *
 * Here's an example of inserting a document with a date field
 *
 * [source,$lang]
 * ----
 * {@link examples.Examples#example13_0}
 * ----
 *
 * == Configuring the service
 *
 * The service is configured with a json object.
 *
 * The following configuration is supported by the mongo service:
 *
 * `address`:: The event bus address used by the service proxy. Defaults to `vertx.mongo`. This is only used if deploying
 * the service as a verticle.
 *
 * `db_name`:: Name of the database in the mongoDB instance to use. Defaults to `default_db`
 * `useObjectId`:: Toggle this option to support persisting and retrieving ObjectId's as strings. Defaults to `false`.
 *
 * The mongo service tries to support most options that are allowed by the driver. There are two ways to configure mongo
 * for use by the driver, either by a connection string or by separate configuration options.
 *
 * NOTE: If the connection string is used the mongo service will ignore any driver configuration options.
 *
 * `connection_string`:: The connection string the driver uses to create the client. E.g. `mongodb://localhost:27017`.
 * For more information on the format of the connection string please consult the driver documentation.
 *
 * *Specific driver configuration options*
 *
 * ----
 * {
 *   // Single Cluster Settings
 *   "host" : "17.0.0.1", // string
 *   "port" : 27017,      // int
 *
 *   // Multiple Cluster Settings
 *   "hosts" : [
 *     {
 *       "host" : "cluster1", // string
 *       "port" : 27000       // int
 *     },
 *     {
 *       "host" : "cluster2", // string
 *       "port" : 28000       // int
 *     },
 *     ...
 *   ],
 *   "replicaSet" :  "foo"    // string
 *
 *   // Connection Pool Settings
 *   "maxPoolSize" : 50,                // int
 *   "minPoolSize" : 25,                // int
 *   "maxIdleTimeMS" : 300000,          // long
 *   "maxLifeTimeMS" : 3600000,         // long
 *   "waitQueueMultiple"  : 10,         // int
 *   "waitQueueTimeoutMS" : 10000,      // long
 *   "maintenanceFrequencyMS" : 2000,   // long
 *   "maintenanceInitialDelayMS" : 500, // long
 *
 *   // Credentials / Auth
 *   "username"   : "john",     // string
 *   "password"   : "passw0rd", // string
 *   "authSource" : "some.db"   // string
 *   // Auth mechanism
 *   "authMechanism"     : "GSSAPI",        // string
 *   "gssapiServiceName" : "myservicename", // string
 *
 *   // Socket Settings
 *   "connectTimeoutMS" : 300000, // int
 *   "socketTimeoutMS"  : 100000, // int
 *   "sendBufferSize"    : 8192,  // int
 *   "receiveBufferSize" : 8192,  // int
 *   "keepAlive" : true           // boolean
 *
 *   // Heartbeat socket settings
 *   "heartbeat.socket" : {
 *   "connectTimeoutMS" : 300000, // int
 *   "socketTimeoutMS"  : 100000, // int
 *   "sendBufferSize"    : 8192,  // int
 *   "receiveBufferSize" : 8192,  // int
 *   "keepAlive" : true           // boolean
 *   }
 *
 *   // Server Settings
 *   "heartbeatFrequencyMS" :    1000 // long
 *   "minHeartbeatFrequencyMS" : 500 // long
 * }
 * ----
 *
 * *Driver option descriptions*
 *
 * `host`:: The host the mongoDB instance is running. Defaults to `127.0.0.1`. This is ignored if `hosts` is specified
 * `port`:: The port the mongoDB instance is listening on. Defaults to `27017`. This is ignored if `hosts` is specified
 * `hosts`:: An array representing the hosts and ports to support a mongoDB cluster (sharding / replication)
 * `host`:: A host in the cluster
 * `port`:: The port a host in the cluster is listening on
 * `replicaSet`:: The name of the replica set, if the mongoDB instance is a member of a replica set
 * `maxPoolSize`:: The maximum number of connections in the connection pool. The default value is `100`
 * `minPoolSize`:: The minimum number of connections in the connection pool. The default value is `0`
 * `maxIdleTimeMS`:: The maximum idle time of a pooled connection. The default value is `0` which means there is no limit
 * `maxLifeTimeMS`:: The maximum time a pooled connection can live for. The default value is `0` which means there is no limit
 * `waitQueueMultiple`:: The maximum number of waiters for a connection to become available from the pool. Default value is `500`
 * `waitQueueTimeoutMS`:: The maximum time that a thread may wait for a connection to become available. Default value is `120000` (2 minutes)
 * `maintenanceFrequencyMS`:: The time period between runs of the maintenance job. Default is `0`.
 * `maintenanceInitialDelayMS`:: The period of time to wait before running the first maintenance job on the connection pool. Default is `0`.
 * `username`:: The username to authenticate. Default is `null` (meaning no authentication required)
 * `password`:: The password to use to authenticate.
 * `authSource`:: The database name associated with the user's credentials. Default value is `admin`
 * `authMechanism`:: The authentication mechanism to use. See [Authentication](http://docs.mongodb.org/manual/core/authentication/) for more details.
 * `gssapiServiceName`:: The Kerberos service name if `GSSAPI` is specified as the `authMechanism`.
 * `connectTimeoutMS`:: The time in milliseconds to attempt a connection before timing out. Default is `10000` (10 seconds)
 * `socketTimeoutMS`:: The time in milliseconds to attempt a send or receive on a socket before the attempt times out. Default is `0` meaning there is no timeout
 * `sendBufferSize`:: Sets the send buffer size (SO_SNDBUF) for the socket. Default is `0`, meaning it will use the OS default for this option.
 * `receiveBufferSize`:: Sets the receive buffer size (SO_RCVBUF) for the socket. Default is `0`, meaning it will use the OS default for this option.
 * `keepAlive`:: Sets the keep alive (SO_KEEPALIVE) for the socket. Default is `false`
 * `heartbeat.socket`:: Configures the socket settings for the cluster monitor of the MongoDB java driver.
 * `heartbeatFrequencyMS`:: The frequency that the cluster monitor attempts to reach each server. Default is `5000` (5 seconds)
 * `minHeartbeatFrequencyMS`:: The minimum heartbeat frequency. The default value is `1000` (1 second)
 *
 * NOTE: Most of the default values listed above use the default values of the MongoDB Java Driver.
 * Please consult the driver documentation for up to date information.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@Document(fileName = "index.adoc")
@GenModule(name = "vertx-mongo")
package io.vertx.ext.mongo;

import io.vertx.codegen.annotations.GenModule;
import io.vertx.docgen.Document;
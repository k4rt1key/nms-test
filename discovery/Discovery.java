package org.nms.discovery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.Logger;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;
import org.nms.api.helpers.Ip;

import org.nms.discovery.helpers.Db;

import static org.nms.discovery.helpers.Db.*;
import static org.nms.discovery.helpers.PingAndPort.*;

public class Discovery extends AbstractVerticle
{
    @Override
    public void start()
    {
        // Run Discovery Handler - Single entry point for discovery process
        vertx.eventBus().localConsumer(Fields.EventBus.RUN_DISCOVERY_ADDRESS, message ->
        {
            var body = (JsonObject) message.body();

            var id = body.getInteger("id");

            runDiscoveryProcess(id)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            Logger.info("Discovery #" + id + " completed successfully");
                            if (message.replyAddress() != null) {
                                message.reply(new JsonObject()
                                        .put("success", true)
                                        .put("id", id));
                            }
                        } else {
                            Logger.error("Error running discovery #" + id + ": " + ar.cause().getMessage());
                            if (message.replyAddress() != null) {
                                message.reply(new JsonObject()
                                        .put("success", false)
                                        .put("id", id)
                                        .put("error", ar.cause().getMessage()));
                            }
                        }
                    });
        });

        Logger.debug("✅ Discovery Verticle Deployed, on thread [ " + Thread.currentThread().getName() + " ] ");
    }

    @Override
    public void stop()
    {
        Logger.info("\uD83D\uDED1 Discovery Verticle Stopped");
    }

    private Future<Void> runDiscoveryProcess(int id)
    {
        Promise<Void> promise = Promise.promise();

        // Step 1: Fetch discovery details
        Db.fetchDiscoveryDetails(id)
                .compose(discovery ->
                {
                    // Step 2: Update discovery status to RUNNING
                    return Db.updateDiscoveryStatus(id, Fields.Discovery.COMPLETED_STATUS)
                            .compose(v ->
                            {
                                // Step 3: Delete existing results
                                return DbEventBus.sendQueryExecutionRequest(
                                        Queries.Discovery.DELETE_RESULT,
                                        new JsonArray().add(id)
                                );
                            })
                            .compose(v ->
                            {
                                // Step 4: Perform discovery process
                                var ipStr = discovery.getString(Fields.Discovery.IP);
                                var ipType = discovery.getString(Fields.Discovery.IP_TYPE);
                                var port = discovery.getInteger(Fields.Discovery.PORT);
                                var credentials = discovery.getJsonArray(Fields.Discovery.CREDENTIAL_JSON);

                                var ipArray = Ip.getIpListAsJsonArray(ipStr, ipType);

                                return executeDiscoverySteps(id, ipArray, port, credentials);
                            });
                })
                .compose(v ->
                {
                    // Step 5: Update discovery status to COMPLETED
                    return Db.updateDiscoveryStatus(id, Fields.Discovery.COMPLETED_STATUS);
                })
                .onComplete(ar ->
                {
                    if (ar.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        // If any step fails, update status to FAILED and complete with failure
                        Db.updateDiscoveryStatus(id, Fields.DiscoveryResult.FAILED_STATUS)
                                .onComplete(v -> promise.fail(ar.cause()));
                    }
                });

        return promise.future();
    }

    private Future<Void> executeDiscoverySteps(int id, JsonArray ipArray, int port, JsonArray credentials)
    {
        Promise<Void> promise = Promise.promise();

        // Step 1: Ping Check - directly call without event bus
        pingIps(ipArray)
                .compose(pingResults ->
                {
                    var pingPassedIps = processPingResults(id, pingResults);


                    // Step 2: Port Check - directly call without event bus
                    return checkPorts(pingPassedIps, port)
                            .compose(portResults ->
                            {
                                var portPassedIps = processPortCheckResults(id, portResults);

                                // Step 3: Credentials Check - only for IPs that passed port check
                                if (portPassedIps.isEmpty())
                                {
                                    Logger.info("No IPs passed port check for discovery #" + id);
                                    return Future.succeededFuture();
                                }

                                // Prepare plugin discovery request
                                var discoveryRequest = new JsonObject()
                                        .put(Fields.PluginDiscoveryRequest.TYPE, "discovery")
                                        .put(Fields.PluginDiscoveryRequest.ID, id)
                                        .put(Fields.PluginDiscoveryRequest.IPS, portPassedIps)
                                        .put(Fields.PluginDiscoveryRequest.PORT, port)
                                        .put(Fields.PluginDiscoveryRequest.CREDENTIALS, credentials);


                                // Send discovery request to plugin via event bus
                                return sendDiscoveryRequestToPlugin(discoveryRequest)
                                        .compose(credentialResults ->
                                                processCredentialCheckResults(id, credentialResults));
                            });
                })
                .onComplete(ar ->
                {
                    if (ar.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        promise.fail(ar.cause());
                    }
                });

        return promise.future();
    }

    private Future<JsonArray> sendDiscoveryRequestToPlugin(JsonObject request)
    {
        Promise<JsonArray> promise = Promise.promise();

        vertx.eventBus().request(Fields.EventBus.PLUGIN_ADDRESS, request, reply ->
        {
            if (reply.succeeded())
            {
                var response = (JsonObject) reply.result().body();
                if (response.containsKey("error"))
                {
                    Logger.error("Error from plugin: " + response.getString("error"));
                    promise.complete(new JsonArray());
                }
                else
                {
                    promise.complete(response.getJsonArray("result", new JsonArray()));
                }
            }
            else
            {
                Logger.error("Error calling plugin: " + reply.cause().getMessage());
                promise.complete(new JsonArray());
            }
        });

        return promise.future();
    }

}
package org.nms.discovery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.nms.App.logger;

import org.nms.api.Utility;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

import static org.nms.discovery.CrudHelpers.*;
import static org.nms.discovery.ConnectionHelpers.*;

public class Discovery extends AbstractVerticle
{
    @Override
    public void start()
    {
        vertx.eventBus().<JsonObject>localConsumer(Fields.EventBus.RUN_DISCOVERY_ADDRESS, message ->
        {
            var body =  message.body();

            var id = body.getInteger(Fields.Discovery.ID);

            runDiscoveryProcess(id)
                    .onComplete(asyncResult ->
                    {
                        if (asyncResult.succeeded())
                        {
                            logger.info("Discovery with id " + id + " completed successfully");
                        }
                        else
                        {
                            logger.error("Error running discovery id " + id + ": " + asyncResult.cause().getMessage());
                        }
                    });
        });

        logger.debug("✅ Discovery Verticle Deployed, on thread [ " + Thread.currentThread().getName() + " ] ");
    }

    @Override
    public void stop()
    {
        logger.info("\uD83D\uDED1 Discovery Verticle Stopped");
    }

    private Future<Void> runDiscoveryProcess(int id)
    {
        var promise = Promise.promise();

        // Step 1: Fetch discovery details
        CrudHelpers.fetchDiscoveryDetails(id)
                .compose(discovery ->
                {
                    // Step 2: Update discovery status to RUNNING
                    return CrudHelpers.updateDiscoveryStatus(id, Fields.Discovery.COMPLETED_STATUS)

                            .compose(statusUpdationResult ->
                            {
                                // Step 3: Delete existing results
                                return DbUtility.sendQueryExecutionRequest (
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

                                var ips = Utility.getIpsFromString(ipStr, ipType);

                                logger.debug("Ips " + ips.encode());

                                return executeDiscoverySteps(id, ips, port, credentials);
                            });
                })

                .compose(discoveryRunResult ->
                {
                    // Step 5: Update discovery status to COMPLETED
                    return CrudHelpers.updateDiscoveryStatus(id, Fields.Discovery.COMPLETED_STATUS);
                })

                .onComplete(statusUpdationStatus ->
                {
                    if (statusUpdationStatus.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        // If any step fails, update status to FAILED and complete with failure
                        CrudHelpers.updateDiscoveryStatus(id, Fields.DiscoveryResult.FAILED_STATUS)
                                .onComplete(updateStatusToFailResult -> promise.fail(statusUpdationStatus.cause().getMessage()));
                    }
                });

        return Future.succeededFuture();
    }

    private Future<Void> executeDiscoverySteps(int id, JsonArray ips, int port, JsonArray credentials)
    {
        Promise<Void> promise = Promise.promise();

        // Step 1: Ping Check - directly call without event bus
        pingIps(ips)
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
                                    logger.info("No IPs passed port check for discovery with id " + id);

                                    return Future.succeededFuture();
                                }

                                // Prepare plugin discovery request
                                var discoveryRequest = new JsonObject()
                                        .put(Fields.PluginDiscoveryRequest.TYPE, Fields.PluginDiscoveryRequest.DISCOVERY)

                                        .put(Fields.PluginDiscoveryRequest.ID, id)

                                        .put(Fields.PluginDiscoveryRequest.IPS, portPassedIps)

                                        .put(Fields.PluginDiscoveryRequest.PORT, port)

                                        .put(Fields.PluginDiscoveryRequest.CREDENTIALS, credentials);


                                // Send discovery request to plugin via event bus
                                return sendDiscoveryRequestToPlugin(discoveryRequest)
                                        .compose(asyncResult -> processCredentialCheckResults(id, asyncResult));
                            });
                })

                .onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        promise.complete();
                    }
                    else
                    {
                        promise.fail(asyncResult.cause());
                    }
                });

        return promise.future();
    }

    private Future<JsonArray> sendDiscoveryRequestToPlugin(JsonObject request)
    {
        Promise<JsonArray> promise = Promise.promise();

        vertx.eventBus().<JsonObject>request(Fields.EventBus.PLUGIN_SPAWN_ADDRESS, request, reply ->
        {
            if (reply.succeeded())
            {
                var response = reply.result().body();

                logger.debug("Plugin response: " + response.encode());

                promise.complete(response.getJsonArray(Fields.Discovery.RESULT_JSON, new JsonArray()));
            }
            else
            {
                logger.error("Error calling plugin: " + reply.cause().getMessage());

                promise.complete(new JsonArray());
            }
        });

        return promise.future();
    }

}
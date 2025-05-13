package org.nms.api.handlers;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import org.nms.ConsoleLogger;
import org.nms.api.helpers.HttpResponse;
import org.nms.api.helpers.Ip;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

import java.util.ArrayList;
import java.util.List;

import static org.nms.App.vertx;

public class DiscoveryHandler
{
    public static void getAllDiscoveries(RoutingContext ctx)
    {
       DbEngine.execute(Queries.Discovery.GET_ALL)
                .onSuccess(discoveries ->
                {
                    // Discoveries not found
                    if (discoveries.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                        return;
                    }

                    // Discoveries found
                    HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        DbEngine.execute(Queries.Discovery.GET_BY_ID,  new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Discovery not found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found
                    HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryResultsById(RoutingContext ctx)
    {
        int id = Integer.parseInt(ctx.request().getParam("id"));

        DbEngine.execute(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Discovery not found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found
                    HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void getDiscoveryResults(RoutingContext ctx)
    {
        DbEngine.execute(Queries.Discovery.GET_ALL_WITH_RESULTS)
                .onSuccess(discoveries ->
                {
                    // Discoveries not found
                    if (discoveries.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                        return;
                    }

                    // Discoveries found
                    HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void createDiscovery(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");

        var ip = ctx.body().asJsonObject().getString("ip");

        var ipType = ctx.body().asJsonObject().getString("ip_type");

        var credentials = ctx.body().asJsonObject().getJsonArray("credentials");

        var port = ctx.body().asJsonObject().getInteger("port");

        // Step 1: Create discovery
        DbEngine.execute(Queries.Discovery.INSERT ,new JsonArray().add(name).add(ip).add(ipType).add(port))
                .onSuccess(discovery ->
                {
                    // !!!! Discovery Not Created
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 400, "Failed to create discovery");
                        return;
                    }

                    // Step 2: Add credentials to discovery
                    List<Tuple> credentialsToAdd = new ArrayList<>();

                    var discoveryId = discovery.getJsonObject(0).getInteger(Fields.Discovery.ID);

                    for (int i = 0; i < credentials.size(); i++)
                    {
                        var credentialId = Integer.parseInt(credentials.getString(i));

                        credentialsToAdd.add(Tuple.of(discoveryId, credentialId));
                    }

                    // Save Credentials Into DB
                    DbEngine.execute(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd)
                            .onSuccess(discoveryCredentials ->
                            {
                                // !!! Credentials Not Created
                                if (discoveryCredentials.isEmpty())
                                {
                                    HttpResponse.sendFailure(ctx, 400, "Failed to add credentials to discovery");
                                    return;
                                }

                                // Step 3: Get complete discovery with credentials
                                DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId))
                                        .onSuccess(discoveryWithCredentials -> HttpResponse.sendSuccess(ctx, 201, "Discovery created successfully", discoveryWithCredentials))
                                        .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                            })
                            .onFailure(err ->
                            {
                                // Rollback discovery creation if adding credentials fails
                                DbEngine.execute(Queries.Discovery.DELETE, new JsonArray().add(discoveryId))
                                        .onFailure(rollbackErr -> ConsoleLogger.error("Failed to rollback discovery creation: " + rollbackErr.getMessage()));

                                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage());
                            });
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // Step-1: Check if discovery Exist
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery Already run
                    if(discovery.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.DiscoveryResult.COMPLETED_STATUS))
                    {
                        HttpResponse.sendFailure(ctx, 400, "Discovery Already Run");
                        return;
                    }

                    // Discovery found, proceed with update
                    var name = ctx.body().asJsonObject().getString("name");

                    var ip = ctx.body().asJsonObject().getString("ip");

                    var ipType = ctx.body().asJsonObject().getString("ip_type");

                    var port = ctx.body().asJsonObject().getInteger("port");

                    // Update Discovery
                    DbEngine.execute(Queries.Discovery.UPDATE, new JsonArray().add(id).add(name).add(ip).add(ipType).add(port))
                            .onSuccess(updatedDiscovery ->
                            {
                                // !!! Discovery Not Updated
                                if (updatedDiscovery.isEmpty())
                                {
                                    HttpResponse.sendFailure(ctx, 400, "Failed to update discovery");
                                    return;
                                }

                                // Get updated discovery with credentials
                                DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                                        .onSuccess(discoveryWithCredentials -> HttpResponse.sendSuccess(ctx, 200, "Discovery updated successfully", discoveryWithCredentials))
                                        .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                            })
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void updateDiscoveryCredentials(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // Step-1: Check if discovery exists
        DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    // !!! Discovery Not Found
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    var addCredentials = ctx.body().asJsonObject().getJsonArray("add_credentials");

                    var removeCredentials = ctx.body().asJsonObject().getJsonArray("remove_credentials");

                    // Step 2: Add credentials to discovery if any
                    List<Tuple> credentialsToAdd = new ArrayList<>();

                    if (addCredentials != null && !addCredentials.isEmpty()) {
                        for (int i = 0; i < addCredentials.size(); i++)
                        {
                            var credentialId = Integer.parseInt(addCredentials.getString(i));
                            credentialsToAdd.add(Tuple.of(id, credentialId));
                        }

                        DbEngine.execute(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd)
                                .onSuccess(addedCredentials ->
                                {
                                    // Step 2: Remove credentials if any
                                    processRemoveCredentials(ctx, id, removeCredentials);
                                })
                                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                    }
                    else
                    {
                        // If no credentials to add, proceed to remove credentials
                        processRemoveCredentials(ctx, id, removeCredentials);
                    }
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    // Helper method to process removing credentials
    private static void processRemoveCredentials(RoutingContext ctx, int discoveryId, JsonArray removeCredentials)
    {
        if (removeCredentials != null && !removeCredentials.isEmpty())
        {
            List<Tuple> credentialsToRemove = new ArrayList<>();

            for (int i = 0; i < removeCredentials.size(); i++)
            {
                var credentialId = Integer.parseInt(removeCredentials.getString(i));
                credentialsToRemove.add(Tuple.of(discoveryId, credentialId));
            }

            DbEngine.execute(Queries.Discovery.DELETE_CREDENTIAL, credentialsToRemove)
                    .onSuccess(v ->
                    {
                        // Return updated discovery with credentials
                        returnUpdatedDiscovery(ctx, discoveryId);
                    })
                    .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
        }
        else
        {
            // If no credentials to remove, just return the updated discovery
            returnUpdatedDiscovery(ctx, discoveryId);
        }
    }

    // Helper method to return updated discovery
    private static void returnUpdatedDiscovery(RoutingContext ctx, int discoveryId)
    {
        DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId))
                .onSuccess(updatedDiscovery -> HttpResponse.sendSuccess(ctx, 200, "Discovery credentials updated successfully", updatedDiscovery))
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void deleteDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        DbEngine.execute(Queries.Discovery.DELETE, new JsonArray().add(id))
                .onSuccess(discovery ->
                {
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    // Discovery found, proceed with delete
                    DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                            .onSuccess(deletedDiscovery -> HttpResponse.sendSuccess(ctx, 200, "Discovery deleted successfully", discovery))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    public static void runDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // Step 1: Check if discovery exists
        DbEngine.execute(Queries.Discovery.GET_BY_ID, new JsonArray().add(id))
                .compose(discoveryWithCredentials ->
                {
                    if (discoveryWithCredentials.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return Future.failedFuture(new Exception("Discovery not found"));
                    }

                    // Step 2: Prepare Discovery Input
                    var discoveryData = discoveryWithCredentials.getJsonObject(0);
                    var ips = discoveryData.getString(Fields.Discovery.IP);
                    var ipType = discoveryData.getString(Fields.Discovery.IP_TYPE);
                    var port = discoveryData.getInteger(Fields.Discovery.PORT);
                    var credentialsToCheck = discoveryData.getJsonArray(Fields.Discovery.CREDENTIAL_JSON);

                    // Convert IPs to correct format
                    var ipArray = Ip.getIpListAsJsonArray(ips, ipType);

                    // Step 3: Delete existing results for this discovery
                    return DbEngine.execute(Queries.Discovery.DELETE_RESULT, new JsonArray().add(id))
                            .compose(v -> {
                                // Step 4: Perform complete discovery process using Event Bus
                                return performDiscoveryViaEventBus(id, ipArray, port, credentialsToCheck);
                            });

                })

                .compose(v -> {
                    // Step 5: Update discovery status to COMPLETED
                    return DbEngine.execute(Queries.Discovery.UPDATE_STATUS, new JsonArray().add(id).add("COMPLETED"));
                })

                .compose(v ->
                {
                    // Step 6: Retrieve final discovery results
                    return DbEngine.execute(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(id))
                            .onSuccess(response -> HttpResponse.sendSuccess(ctx, 200, "Discovery run successfully", response))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })

                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
    }

    private static Future<Void> performDiscoveryViaEventBus(int id, JsonArray ipArray, int port, JsonArray credentialsToCheck)
    {
        Promise<Void> promise = Promise.promise();

        // Prepare Discovery Input
        var discoveryInput = new JsonObject()
                .put(Fields.PluginDiscoveryRequest.ID, id)
                .put(Fields.PluginDiscoveryRequest.IPS, ipArray)
                .put(Fields.PluginDiscoveryRequest.PORT, port)
                .put(Fields.PluginDiscoveryRequest.CREDENTIALS, credentialsToCheck);

        // Ping Check via Event Bus
        vertx.eventBus().request(Fields.EventBus.PING_CHECK_ADDRESS,
                new JsonObject().put(Fields.PluginDiscoveryRequest.IPS, ipArray),
                pingCheckReply -> {
                    if (pingCheckReply.failed()) {
                        promise.fail(pingCheckReply.cause());
                        return;
                    }

                    var pingResults = (JsonArray) pingCheckReply.result().body();
                    var pingCheckPassedIps = processPingResults(id, pingResults);

                    // Port Check via Event Bus
                    vertx.eventBus().request(Fields.EventBus.PORT_CHECK_ADDRESS,
                            new JsonObject()
                                    .put(Fields.PluginDiscoveryRequest.IPS, pingCheckPassedIps)
                                    .put(Fields.PluginDiscoveryRequest.PORT, port),
                            portCheckReply -> {
                                if (portCheckReply.failed()) {
                                    promise.fail(portCheckReply.cause());
                                    return;
                                }

                                var portResults = (JsonArray) portCheckReply.result().body();
                                var portCheckPassedIps = processPortCheckResults(id, portResults);

                                // Combined Discovery Check via Event Bus
                                vertx.eventBus().request(Fields.EventBus.DISCOVERY_ADDRESS,
                                        new JsonObject()
                                                .put(Fields.PluginDiscoveryRequest.ID, id)
                                                .put(Fields.PluginDiscoveryRequest.IPS, portCheckPassedIps)
                                                .put(Fields.PluginDiscoveryRequest.PORT, port)
                                                .put(Fields.PluginDiscoveryRequest.CREDENTIALS, credentialsToCheck),
                                        discoveryCheckReply -> {
                                            if (discoveryCheckReply.failed()) {
                                                promise.fail(discoveryCheckReply.cause());
                                                return;
                                            }

                                            var credentialResults = (JsonArray) discoveryCheckReply.result().body();
                                            processCredentialCheckResults(id, credentialResults)
                                                    .onComplete(handler -> {
                                                        if (handler.succeeded()) {
                                                            promise.complete();
                                                        } else {
                                                            promise.fail(handler.cause());
                                                        }
                                                    });
                                        });
                            });
                });

        return promise.future();
    }

    private static JsonArray processPingResults(int id, JsonArray pingResults)
    {
        var pingCheckPassedIps = new JsonArray();
        var pingCheckFailedIps = new ArrayList<Tuple>();

        for (int i = 0; i < pingResults.size(); i++) {
            var result = pingResults.getJsonObject(i);
            var success = result.getBoolean("success");
            var ip = result.getString("ip");

            if (success) {
                pingCheckPassedIps.add(ip);
            } else {
                pingCheckFailedIps.add(Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Insert failed IPs if any
        if (!pingCheckFailedIps.isEmpty()) {
            DbEngine.execute(Queries.Discovery.INSERT_RESULT, pingCheckFailedIps);
        }

        return pingCheckPassedIps;
    }

    private static JsonArray processPortCheckResults(int id, JsonArray portCheckResults)
    {
        var portCheckPassedIps = new JsonArray();
        var portCheckFailedIps = new ArrayList<Tuple>();

        for (int i = 0; i < portCheckResults.size(); i++) {
            var result = portCheckResults.getJsonObject(i);
            var success = result.getBoolean("success");
            var ip = result.getString("ip");

            if (success) {
                portCheckPassedIps.add(ip);
            } else {
                portCheckFailedIps.add(Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Insert failed IPs if any
        if (!portCheckFailedIps.isEmpty()) {
            DbEngine.execute(Queries.Discovery.INSERT_RESULT, portCheckFailedIps);
        }

        return portCheckPassedIps;
    }

    private static Future<Void> processCredentialCheckResults(int id, JsonArray credentialCheckResults)
    {
        var credentialCheckFailedIps = new ArrayList<Tuple>();
        var credentialCheckSuccessIps = new ArrayList<Tuple>();

        for (int i = 0; i < credentialCheckResults.size(); i++) {
            var result = credentialCheckResults.getJsonObject(i);
            var success = result.getBoolean("success");
            var ip = result.getString(Fields.PluginDiscoveryResponse.IP);

            if (success) {
                var credential = result.getJsonObject(Fields.PluginDiscoveryResponse.CREDENTIALS).getInteger(Fields.Credential.ID);
                credentialCheckSuccessIps.add(Tuple.of(id, credential, ip, result.getString("message"), "COMPLETED"));
            } else {
                credentialCheckFailedIps.add(Tuple.of(id, null, ip, result.getString("message"), "FAILED"));
            }
        }

        // Batch insert results, handling empty lists to avoid batch query errors
        Future<JsonArray> failureFuture = credentialCheckFailedIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbEngine.execute(Queries.Discovery.INSERT_RESULT, credentialCheckFailedIps);

        Future<JsonArray> successFuture = credentialCheckSuccessIps.isEmpty()
                ? Future.succeededFuture(new JsonArray())
                : DbEngine.execute(Queries.Discovery.INSERT_RESULT, credentialCheckSuccessIps);

        return Future.join(failureFuture, successFuture)
                .compose(v -> Future.succeededFuture());
    }
}
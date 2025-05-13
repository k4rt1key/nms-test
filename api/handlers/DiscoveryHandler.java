package org.nms.api.handlers;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import org.nms.Logger;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Config;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

import java.util.ArrayList;

import static org.nms.App.vertx;

public class DiscoveryHandler
{
    public static void getAllDiscoveries(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_ALL);
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discoveries = ar.result();
                if (discoveries.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discovery = ar.result();
                if (discovery.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryResultsById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(id));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discovery = ar.result();
                if (discovery.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Discovery found", discovery);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryResults(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_ALL_WITH_RESULTS);
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discoveries = ar.result();
                if (discoveries.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "No discoveries found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Discoveries found", discoveries);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void createDiscovery(RoutingContext ctx)
    {
        var name = ctx.body().asJsonObject().getString("name");
        var ip = ctx.body().asJsonObject().getString("ip");
        var ipType = ctx.body().asJsonObject().getString("ip_type");
        var credentials = ctx.body().asJsonObject().getJsonArray("credentials");
        var port = ctx.body().asJsonObject().getInteger("port");

        var insertRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT, new JsonArray().add(name).add(ip).add(ipType).add(port));
        insertRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discovery = ar.result();
                if (discovery.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 400, "Failed to create discovery");
                    return;
                }

                var credentialsToAdd = new ArrayList<Tuple>();
                var discoveryId = discovery.getJsonObject(0).getInteger(Fields.Discovery.ID);

                for (var i = 0; i < credentials.size(); i++)
                {
                    var credentialId = Integer.parseInt(credentials.getString(i));
                    credentialsToAdd.add(Tuple.of(discoveryId, credentialId));
                }

                var credentialRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd);
                credentialRequest.onComplete(credAr ->
                {
                    if (credAr.succeeded())
                    {
                        var discoveryCredentials = credAr.result();
                        if (discoveryCredentials.isEmpty())
                        {
                            HttpResponse.sendFailure(ctx, 400, "Failed to add credentials to discovery");
                            return;
                        }

                        var getRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId));
                        getRequest.onComplete(getAr ->
                        {
                            if (getAr.succeeded())
                            {
                                var discoveryWithCredentials = getAr.result();
                                HttpResponse.sendSuccess(ctx, 201, "Discovery created successfully", discoveryWithCredentials);
                            }
                            else
                            {
                                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", getAr.cause().getMessage());
                            }
                        });
                    }
                    else
                    {
                        var rollbackRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.DELETE, new JsonArray().add(discoveryId));
                        rollbackRequest.onComplete(rollbackAr ->
                        {
                            if (rollbackAr.failed())
                            {
                                Logger.error("Failed to rollback discovery creation: " + rollbackAr.cause().getMessage());
                            }
                        });
                        HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", credAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void updateDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discovery = ar.result();
                if (discovery.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                    return;
                }

                if (discovery.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.DiscoveryResult.COMPLETED_STATUS))
                {
                    HttpResponse.sendFailure(ctx, 400, "Discovery Already Run");
                    return;
                }

                var name = ctx.body().asJsonObject().getString("name");
                var ip = ctx.body().asJsonObject().getString("ip");
                var ipType = ctx.body().asJsonObject().getString("ip_type");
                var port = ctx.body().asJsonObject().getInteger("port");

                var updateRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.UPDATE, new JsonArray().add(id).add(name).add(ip).add(ipType).add(port));
                updateRequest.onComplete(updateAr ->
                {
                    if (updateAr.succeeded())
                    {
                        var updatedDiscovery = updateAr.result();
                        if (updatedDiscovery.isEmpty())
                        {
                            HttpResponse.sendFailure(ctx, 400, "Failed to update discovery");
                            return;
                        }

                        var getRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id));
                        getRequest.onComplete(getAr ->
                        {
                            if (getAr.succeeded())
                            {
                                var discoveryWithCredentials = getAr.result();
                                HttpResponse.sendSuccess(ctx, 200, "Discovery updated successfully", discoveryWithCredentials);
                            }
                            else
                            {
                                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", getAr.cause().getMessage());
                            }
                        });
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", updateAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void updateDiscoveryCredentials(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discovery = ar.result();
                if (discovery.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                    return;
                }

                var addCredentials = ctx.body().asJsonObject().getJsonArray("add_credentials");
                var removeCredentials = ctx.body().asJsonObject().getJsonArray("remove_credentials");

                if (addCredentials != null && !addCredentials.isEmpty())
                {
                    var credentialsToAdd = new ArrayList<Tuple>();
                    for (var i = 0; i < addCredentials.size(); i++)
                    {
                        var credentialId = Integer.parseInt(addCredentials.getString(i));
                        credentialsToAdd.add(Tuple.of(id, credentialId));
                    }

                    var addRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd);
                    addRequest.onComplete(addAr ->
                    {
                        if (addAr.succeeded())
                        {
                            processRemoveCredentials(ctx, id, removeCredentials);
                        }
                        else
                        {
                            HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", addAr.cause().getMessage());
                        }
                    });
                }
                else
                {
                    processRemoveCredentials(ctx, id, removeCredentials);
                }
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    // Helper
    private static void processRemoveCredentials(RoutingContext ctx, int discoveryId, JsonArray removeCredentials)
    {
        if (removeCredentials != null && !removeCredentials.isEmpty())
        {
            var credentialsToRemove = new ArrayList<Tuple>();
            for (var i = 0; i < removeCredentials.size(); i++)
            {
                var credentialId = Integer.parseInt(removeCredentials.getString(i));
                credentialsToRemove.add(Tuple.of(discoveryId, credentialId));
            }

            var deleteRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.DELETE_CREDENTIAL, credentialsToRemove);
            deleteRequest.onComplete(ar ->
            {
                if (ar.succeeded())
                {
                    returnUpdatedDiscovery(ctx, discoveryId);
                }
                else
                {
                    HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
                }
            });
        }
        else
        {
            returnUpdatedDiscovery(ctx, discoveryId);
        }
    }

    // Helper
    private static void returnUpdatedDiscovery(RoutingContext ctx, int discoveryId)
    {
        var getRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId));
        getRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var updatedDiscovery = ar.result();
                HttpResponse.sendSuccess(ctx, 200, "Discovery credentials updated successfully", updatedDiscovery);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void deleteDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var deleteRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.DELETE, new JsonArray().add(id));
        deleteRequest.onComplete(ar ->
        {
            if (ar.succeeded()) {
                var discovery = ar.result();
                if (discovery.isEmpty()) {
                    HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                    return;
                }

                HttpResponse.sendSuccess(ctx, 200, "Discovery deleted successfully", discovery);

            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void runDiscovery(RoutingContext ctx)
    {
        try
        {
            var id = Integer.parseInt(ctx.request().getParam("id"));
            var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id));

            checkRequest.onComplete(ar ->
            {
                if (ar.succeeded())
                {
                    var discovery = ar.result();
                    if (discovery.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Discovery not found");
                        return;
                    }

                    vertx.eventBus().send(
                            Fields.EventBus.RUN_DISCOVERY_ADDRESS,
                            new JsonObject().put("id", id)
                    );

                    HttpResponse.sendSuccess(ctx, 202, "Discovery request with id " + id + " accepted and is being processed",
                            new JsonArray());
                }
                else
                {
                    HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
                }
            });
        }
        catch (Exception e)
        {
            HttpResponse.sendFailure(ctx, 400, "Invalid discovery ID provided");
        }
    }
}
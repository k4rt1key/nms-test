package org.nms.api.handlers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Tuple;
import static org.nms.App.logger;

import org.nms.api.Utility;
import org.nms.api.Validators;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

import java.util.ArrayList;

import static org.nms.App.vertx;

public class Discovery
{
    public static void getAllDiscoveries(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_ALL).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discoveries = asyncResult.result();

                if (discoveries.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "No discoveries found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Discoveries found", discoveries);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryById(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discovery = asyncResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Discovery not found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Discovery found", discovery);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryResultsById(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discovery = asyncResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Discovery not found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Discovery found", discovery);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getDiscoveryResults(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_ALL_WITH_RESULTS).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discoveries = asyncResult.result();

                if (discoveries.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "No discoveries found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Discoveries found", discoveries);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void createDiscovery(RoutingContext ctx)
    {
        if(Validators.validateCreateDiscovery(ctx)) { return; }

        var name = ctx.body().asJsonObject().getString(Fields.Discovery.NAME);

        var ip = ctx.body().asJsonObject().getString(Fields.Discovery.IP);

        var ipType = ctx.body().asJsonObject().getString(Fields.Discovery.IP_TYPE);

        var credentials = ctx.body().asJsonObject().getJsonArray(Fields.Discovery.CREDENTIAL_JSON);

        var port = ctx.body().asJsonObject().getInteger(Fields.Discovery.PORT);

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT, new JsonArray().add(name).add(ip).add(ipType).add(port)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discovery = asyncResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 400, "Failed to create discovery");

                    return;
                }

                var credentialsToAdd = new ArrayList<Tuple>();

                var discoveryId = discovery.getJsonObject(0).getInteger(Fields.Discovery.ID);

                for (var i = 0; i < credentials.size(); i++)
                {
                    var credentialId = Integer.parseInt(credentials.getString(i));

                    credentialsToAdd.add(Tuple.of(discoveryId, credentialId));
                }

                var credentialRequest = DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd);

                credentialRequest.onComplete(credentialInsertion ->
                {
                    if (credentialInsertion.succeeded())
                    {
                        var discoveryCredentials = credentialInsertion.result();

                        if (discoveryCredentials.isEmpty())
                        {
                            Utility.sendFailure(ctx, 400, "Failed to add credentials to discovery");
                            return;
                        }

                        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId)).onComplete(discoveryResult ->
                        {
                            if (discoveryResult.succeeded())
                            {
                                var discoveryWithCredentials = discoveryResult.result();

                                Utility.sendSuccess(ctx, 201, "Discovery created successfully", discoveryWithCredentials);
                            }
                            else
                            {
                                Utility.sendFailure(ctx, 500, "Something Went Wrong", discoveryResult.cause().getMessage());
                            }
                        });
                    }
                    else
                    {
                        DbUtility.sendQueryExecutionRequest(Queries.Discovery.DELETE, new JsonArray().add(discoveryId)).onComplete(rollbackResult ->
                        {
                            if (rollbackResult.failed())
                            {
                                logger.error("Failed to rollback discovery creation: " + rollbackResult.cause().getMessage());
                            }
                        });

                        Utility.sendFailure(ctx, 500, "Something Went Wrong", credentialInsertion.cause().getMessage());
                    }
                });
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void updateDiscovery(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        if(Validators.validateUpdateDiscovery(ctx)) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discovery = asyncResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Discovery not found");

                    return;
                }

                if (discovery.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.DiscoveryResult.COMPLETED_STATUS))
                {
                    Utility.sendFailure(ctx, 400, "Discovery Already Run");

                    return;
                }

                var name = ctx.body().asJsonObject().getString(Fields.Discovery.NAME);

                var ip = ctx.body().asJsonObject().getString(Fields.Discovery.IP);

                var ipType = ctx.body().asJsonObject().getString(Fields.Discovery.IP_TYPE);

                var port = ctx.body().asJsonObject().getInteger(Fields.Discovery.PORT);

                DbUtility.sendQueryExecutionRequest(Queries.Discovery.UPDATE, new JsonArray().add(id).add(name).add(ip).add(ipType).add(port)).onComplete(updateResult ->
                {
                    if (updateResult.succeeded())
                    {
                        var updatedDiscovery = updateResult.result();

                        if (updatedDiscovery.isEmpty())
                        {
                            Utility.sendFailure(ctx, 400, "Failed to update discovery");

                            return;
                        }

                        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id)).onComplete(discoveryResult ->
                        {
                            if (discoveryResult.succeeded())
                            {
                                var discoveryWithCredentials = discoveryResult.result();

                                Utility.sendSuccess(ctx, 200, "Discovery updated successfully", discoveryWithCredentials);
                            }
                            else
                            {
                                Utility.sendFailure(ctx, 500, "Something Went Wrong", discoveryResult.cause().getMessage());
                            }
                        });
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Something Went Wrong", updateResult.cause().getMessage());
                    }
                });
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void updateDiscoveryCredentials(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        if( Validators.validateUpdateDiscoveryCredential(ctx)) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id)).onComplete(discoveryResult ->
        {
            if (discoveryResult.succeeded())
            {
                var discovery = discoveryResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Discovery not found");

                    return;
                }

                var addCredentials = ctx.body().asJsonObject().getJsonArray(Fields.Discovery.ADD_CREDENTIALS);

                var removeCredentials = ctx.body().asJsonObject().getJsonArray(Fields.Discovery.REMOVE_CREDENTIALS);

                if (addCredentials != null && !addCredentials.isEmpty())
                {
                    var credentialsToAdd = new ArrayList<Tuple>();

                    for (var i = 0; i < addCredentials.size(); i++)
                    {
                        var credentialId = Integer.parseInt(addCredentials.getString(i));

                        credentialsToAdd.add(Tuple.of(id, credentialId));
                    }

                    DbUtility.sendQueryExecutionRequest(Queries.Discovery.INSERT_CREDENTIAL, credentialsToAdd).onComplete(discoveryInsertion ->
                    {
                        if (discoveryInsertion.succeeded())
                        {
                            processRemoveCredentials(ctx, id, removeCredentials);
                        }
                        else
                        {
                            Utility.sendFailure(ctx, 500, "Something Went Wrong", discoveryInsertion.cause().getMessage());
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
                Utility.sendFailure(ctx, 500, "Something Went Wrong", discoveryResult.cause().getMessage());
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

            DbUtility.sendQueryExecutionRequest(Queries.Discovery.DELETE_CREDENTIAL, credentialsToRemove).onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    returnUpdatedDiscovery(ctx, discoveryId);
                }
                else
                {
                    Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
                }
            });
        }
        else
        {
            returnUpdatedDiscovery(ctx, discoveryId);
        }
    }

    // Helper method to
    private static void returnUpdatedDiscovery(RoutingContext ctx, int discoveryId)
    {
        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(discoveryId)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var updatedDiscovery = asyncResult.result();

                Utility.sendSuccess(ctx, 200, "Discovery credentials updated successfully", updatedDiscovery);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void deleteDiscovery(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.DELETE, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discovery = asyncResult.result();

                if (discovery.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Discovery not found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Discovery deleted successfully", discovery);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void runDiscovery(RoutingContext ctx)
    {
        try
        {
            var id = Validators.validateID(ctx);

            if(id == -1) { return; }

            DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    var discovery = asyncResult.result();

                    if (discovery.isEmpty())
                    {
                        Utility.sendFailure(ctx, 404, "Discovery not found");

                        return;
                    }

                    vertx.eventBus().send(
                            Fields.EventBus.RUN_DISCOVERY_ADDRESS,
                            new JsonObject().put(Fields.Discovery.ID, id)
                    );

                    Utility.sendSuccess(ctx, 202, "Discovery request with id " + id + " accepted and is being processed", new JsonArray());
                }
                else
                {
                    Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            Utility.sendFailure(ctx, 400, "Invalid discovery ID provided");
        }
    }
}
package org.nms.api.handlers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import static org.nms.App.logger;
import static org.nms.constants.Fields.DiscoveryCredential.DISCOVERY_ID;
import static org.nms.constants.Fields.PluginDiscoveryRequest.IPS;
import static org.nms.constants.Fields.PluginPollingRequest.METRIC_GROUPS;

import org.nms.Cache;
import org.nms.api.Utility;
import org.nms.api.Validators;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

import java.util.ArrayList;

public class Provision
{
    public static void getAllProvisions(RoutingContext ctx)
    {
        DbUtility.sendQueryExecutionRequest(Queries.Monitor.GET_ALL).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var monitors = asyncResult.result();

                if (monitors.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "No provisions found");

                    return;
                }
                Utility.sendSuccess(ctx, 200, "Provisions found", monitors);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void getProvisionById(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if(id == -1) { return; }

        DbUtility.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var monitor = asyncResult.result();

                if (monitor.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Provision not found");

                    return;
                }

                Utility.sendSuccess(ctx, 200, "Provision found", monitor);
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void createProvision(RoutingContext ctx)
    {
        if(Validators.validateProvisionCreation(ctx)) { return; }

        var discoveryId = ctx.body().asJsonObject().getString(DISCOVERY_ID);

        var ips = ctx.body().asJsonObject().getJsonArray(IPS);

        DbUtility.sendQueryExecutionRequest(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(discoveryId)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var discoveriesWithResult = asyncResult.result();

                if (discoveriesWithResult == null || discoveriesWithResult.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Failed To Get Discovery with that id");

                    return;
                }

                if (discoveriesWithResult.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.Discovery.PENDING_STATUS))
                {
                    Utility.sendFailure(ctx, 409, "Can't provision pending discovery");

                    return;
                }

                var results = discoveriesWithResult.getJsonObject(0).getJsonArray(Fields.Discovery.RESULT_JSON);

                var addMonitorsFuture = new ArrayList<Future>();

                if (results != null && !results.isEmpty())
                {
                    for (var j = 0; j < ips.size(); j++)
                    {
                        addMonitorsFuture.add(DbUtility.sendQueryExecutionRequest(Queries.Monitor.INSERT, new JsonArray().add(discoveryId).add(ips.getString(j))));
                    }

                    var joinFuture = CompositeFuture.join(addMonitorsFuture);

                    joinFuture.onComplete(provisionsInsertion ->
                    {
                        if (provisionsInsertion.succeeded())
                        {
                            var savedProvisions = provisionsInsertion.result();

                            var provisionArray = new JsonArray();

                            for (var i = 0; i < savedProvisions.size(); i++)
                            {
                                if (savedProvisions.resultAt(i) != null)
                                {
                                    var savedProvision = (JsonArray) savedProvisions.resultAt(i);

                                    provisionArray.add(savedProvision.getJsonObject(0));
                                }
                            }

                            Cache.insertMonitorArray(provisionArray);

                            Utility.sendSuccess(ctx, 200, "Provisioned All Valid Ips", provisionArray);
                        }
                        else
                        {
                            Utility.sendFailure(ctx, 500, "Error during provisioning", provisionsInsertion.cause().getMessage());
                        }
                    });
                }
            }
            else
            {
                Utility.sendFailure(ctx, 400, "Discovery Not Found");
            }
        });
    }

    public static void deleteProvision(RoutingContext ctx)
    {
        var id = Validators.validateID(ctx);

        if( id == -1 ){ return; }

        DbUtility.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id)).onComplete(asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                var provision = asyncResult.result();

                if (provision.isEmpty())
                {
                    Utility.sendFailure(ctx, 404, "Provision not found");

                    return;
                }

                DbUtility.sendQueryExecutionRequest(Queries.Monitor.DELETE, new JsonArray().add(id)).onComplete(monitorDeletion ->
                {
                    if (monitorDeletion.succeeded())
                    {
                        var deletedMonitor = monitorDeletion.result();

                        if (!deletedMonitor.isEmpty())
                        {
                            Cache.deleteMetricGroups(deletedMonitor.getJsonObject(0).getInteger(Fields.MetricGroup.ID));

                            Utility.sendSuccess(ctx, 200, "Provision deleted successfully", provision);
                        }
                        else
                        {
                            Utility.sendFailure(ctx, 400, "Failed to delete Provision");
                        }
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Something Went Wrong", monitorDeletion.cause().getMessage());
                    }
                });
            }
            else
            {
                Utility.sendFailure(ctx, 500, "Something Went Wrong", asyncResult.cause().getMessage());
            }
        });
    }

    public static void updateMetric(RoutingContext ctx)
    {
        if(Validators.validateProvisionUpdation(ctx)) { return; }

        var id = ctx.body().asJsonObject().getString(Fields.Monitor.ID);

        var metrics = ctx.body().asJsonObject().getJsonArray(METRIC_GROUPS);

        DbUtility.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id))
                .compose(monitor ->
                {
                    if (monitor.isEmpty())
                    {
                        Utility.sendFailure(ctx, 404, "Monitor with id " + id + " Not exist");

                        return Future.failedFuture("Monitor with id " + id + " Not exist");
                    }

                    var updateMetricGroupsFuture = new ArrayList<Future<JsonArray>>();

                    for (var i = 0; i < metrics.size(); i++)
                    {
                        var name = metrics.getJsonObject(i).getString(Fields.MetricGroup.NAME);

                        var pollingInterval = metrics.getJsonObject(i).getInteger(Fields.MetricGroup.POLLING_INTERVAL);

                        var isEnabled = metrics.getJsonObject(i).getBoolean(Fields.MetricGroup.IS_ENABLED);

                        updateMetricGroupsFuture.add(DbUtility.sendQueryExecutionRequest(Queries.Monitor.UPDATE, new JsonArray().add(id).add(pollingInterval).add(name).add(isEnabled)));
                    }

                    return Future.join(updateMetricGroupsFuture);

                }).onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        DbUtility.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id)).onComplete(monitorResult ->
                        {
                            if (monitorResult.succeeded())
                            {
                                var res = monitorResult.result();

                                Cache.updateMetricGroups(res.getJsonObject(0).getJsonArray(Fields.Monitor.METRIC_GROUP_JSON));

                                Utility.sendSuccess(ctx, 200, "Updated Provision", res);
                            }
                            else
                            {
                                Utility.sendFailure(ctx, 500, "Failed To Update Discovery", asyncResult.cause().getMessage());
                            }
                        });
                    }
                    else
                    {
                        Utility.sendFailure(ctx, 500, "Error Updating Metric groups", asyncResult.cause().getMessage());
                    }
                });
    }
}
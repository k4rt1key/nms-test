package org.nms.api.handlers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.ConsoleLogger;
import org.nms.cache.MonitorCache;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

import java.util.ArrayList;
import java.util.List;

public class ProvisionHandler
{
    public static void getAllProvisions(RoutingContext ctx)
    {
        DbEngine.execute(Queries.Monitor.GET_ALL)
                .onSuccess(monitors ->
                {
                    // Provisions not found
                    if (monitors.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "No provisions found");
                        return;
                    }

                    // Provisions found
                    HttpResponse.sendSuccess(ctx, 200, "Provisions found", monitors);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void getProvisionById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        DbEngine.execute(Queries.Monitor.GET_BY_ID, new JsonArray().add(id))
                .onSuccess( monitor ->
                {
                    // Provision not found
                    if (monitor.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Provision not found");
                        return;
                    }

                    // Provision found
                    HttpResponse.sendSuccess(ctx, 200, "Provision found", monitor);
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void createProvision(RoutingContext ctx)
    {
        var discovery_id = Integer.parseInt(ctx.body().asJsonObject().getString("discovery_id"));

        var ips = ctx.body().asJsonObject().getJsonArray("ips");

        DbEngine.execute(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(discovery_id))
                // If Discovery Exist ??
                .onSuccess(discoveriesWithResult ->
                {
                    if(discoveriesWithResult == null || discoveriesWithResult.isEmpty() )
                    {
                        HttpResponse.sendFailure(ctx, 404, "Failed To Get Discovery with that id");
                        return;
                    }

                    // If Discovery Pending To Run ??
                    if(discoveriesWithResult.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.Discovery.PENDING_STATUS))
                    {
                        HttpResponse.sendFailure(ctx, 409, "Can't provision pending discovery");
                        return;
                    }

                    var results = discoveriesWithResult.getJsonObject(0).getJsonArray(Fields.Discovery.RESULT_JSON);

                    var addMonitorsFuture = new ArrayList<Future>();

                    if(results != null && !results.isEmpty())
                    {
                        for(var j = 0; j < ips.size(); j++)
                        {
                            addMonitorsFuture.add(DbEngine.execute(Queries.Monitor.INSERT, new JsonArray().add(discovery_id).add(ips.getString(j))));

                            ConsoleLogger.debug(ips.getString(j));
                        }

                        // Save Monitors
                        CompositeFuture.join(addMonitorsFuture)
                                .onSuccess(v -> v.onSuccess(savedProvisions ->
                                {

                                   /*
                                        savedProvision Is Currently In This Format...
                                        [ [ { id: 1, ip: '10.20.41.10', ... } ],  [ { id: 2, ip: '10.20.41.11', ... } ] ]

                                        We Want It In This Format...
                                        [ { id: 1, ip: '10.20.41.10', ... }, { id: 2, ip: '10.20.41.11', ... } ]

                                   */

                                    var provisionArray = new JsonArray();

                                    for(var i = 0; i < savedProvisions.size(); i++)
                                    {
                                        if(savedProvisions.resultAt(i) != null)
                                        {
                                            var savedProvision = (JsonArray) savedProvisions.resultAt(i);

                                            provisionArray.add(savedProvision.getJsonObject(0));
                                        }
                                    }

                                    MonitorCache.insertMonitorArray(provisionArray);

                                    HttpResponse.sendSuccess(ctx, 200, "Provisioned All Valid Ips", provisionArray);

                                }))
                                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Error during provisioning", err.getMessage()));
                    }
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 400, "Discovery Not Found", ""));

    }

    public static void deleteProvision(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        // First check if discovery exists
        DbEngine.execute(Queries.Monitor.GET_BY_ID, new JsonArray().add(id))
                .onSuccess(provision ->
                {
                    if (provision.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Provision not found");
                        return;
                    }

                    // Provision found, proceed with delete
                    DbEngine.execute(Queries.Monitor.DELETE, new JsonArray().add(id))
                            .onSuccess(deletedMonitor ->
                            {
                                if(!deletedMonitor.isEmpty())
                                {
                                    MonitorCache.deleteMetricGroups(deletedMonitor.getJsonObject(0).getInteger(Fields.MetricGroup.ID));
                                    HttpResponse.sendSuccess(ctx, 200, "Provision deleted successfully", provision);
                                }
                                else
                                {
                                    HttpResponse.sendFailure(ctx, 400, "Failed to delete Provision");
                                }
                            })
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));
                })
                .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", err.getMessage()));

    }

    public static void updateMetric(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));

        var metrics = ctx.body().asJsonObject().getJsonArray("metrics");

        DbEngine.execute(Queries.Monitor.GET_BY_ID, new JsonArray().add(id))
                .compose(v ->
                {
                    if(v.isEmpty())
                    {
                        HttpResponse.sendFailure(ctx, 404, "Monitor with id " + id + " Not exist");
                        return Future.failedFuture("Monitor with id " + id + " Not exist");
                    }

                    List<Future<JsonArray>> updateMetricGroupsFuture = new ArrayList<>();

                    for (var i = 0; i < metrics.size(); i++)
                    {
                        var name = metrics.getJsonObject(i).getString(Fields.MetricGroup.NAME);
                        var pollingInterval = metrics.getJsonObject(i).getInteger(Fields.MetricGroup.POLLING_INTERVAL);
                        var isEnabled = metrics.getJsonObject(i).getBoolean(Fields.MetricGroup.IS_ENABLED);

                        updateMetricGroupsFuture.add(DbEngine.execute(Queries.Monitor.UPDATE, new JsonArray().add(id).add(pollingInterval).add(name).add(isEnabled)));
                    }

                    // Update Metric Group
                    return Future.join(updateMetricGroupsFuture)
                            .compose(c ->
                                    DbEngine.execute(Queries.Monitor.GET_BY_ID, new JsonArray().add(id))
                                            .onSuccess(res ->
                                            {
                                                MonitorCache.updateMetricGroups(res.getJsonObject(0).getJsonArray(Fields.Monitor.METRIC_GROUP_JSON));

                                                HttpResponse.sendSuccess(ctx, 200, "Updated Provision", res);
                                            })
                                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Failed To Update Discovery", err.getMessage())))
                            .onFailure(err -> HttpResponse.sendFailure(ctx, 500, "Error Updating Metric groups", err.getMessage()));
                });
    }
}

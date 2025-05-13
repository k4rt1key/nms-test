package org.nms.api.handlers;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.RoutingContext;
import org.nms.Logger;
import org.nms.cache.MonitorCache;
import org.nms.api.helpers.HttpResponse;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

import java.util.ArrayList;

public class ProvisionHandler
{
    public static void getAllProvisions(RoutingContext ctx)
    {
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.GET_ALL);
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var monitors = ar.result();
                if (monitors.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "No provisions found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Provisions found", monitors);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void getProvisionById(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var monitor = ar.result();
                if (monitor.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Provision not found");
                    return;
                }
                HttpResponse.sendSuccess(ctx, 200, "Provision found", monitor);
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void createProvision(RoutingContext ctx)
    {
        var discoveryId = Integer.parseInt(ctx.body().asJsonObject().getString("discovery_id"));
        var ips = ctx.body().asJsonObject().getJsonArray("ips");
        var queryRequest = DbEventBus.sendQueryExecutionRequest(Queries.Discovery.GET_WITH_RESULTS_BY_ID, new JsonArray().add(discoveryId));
        queryRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var discoveriesWithResult = ar.result();
                if (discoveriesWithResult == null || discoveriesWithResult.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Failed To Get Discovery with that id");
                    return;
                }

                if (discoveriesWithResult.getJsonObject(0).getString(Fields.Discovery.STATUS).equals(Fields.Discovery.PENDING_STATUS))
                {
                    HttpResponse.sendFailure(ctx, 409, "Can't provision pending discovery");
                    return;
                }

                var results = discoveriesWithResult.getJsonObject(0).getJsonArray(Fields.Discovery.RESULT_JSON);
                var addMonitorsFuture = new ArrayList<Future>();

                if (results != null && !results.isEmpty())
                {
                    for (var j = 0; j < ips.size(); j++)
                    {
                        addMonitorsFuture.add(DbEventBus.sendQueryExecutionRequest(Queries.Monitor.INSERT, new JsonArray().add(discoveryId).add(ips.getString(j))));
                        Logger.debug(ips.getString(j));
                    }

                    var joinFuture = CompositeFuture.join(addMonitorsFuture);
                    joinFuture.onComplete(joinAr ->
                    {
                        if (joinAr.succeeded())
                        {
                            var savedProvisions = joinAr.result();
                            var provisionArray = new JsonArray();
                            for (var i = 0; i < savedProvisions.size(); i++)
                            {
                                if (savedProvisions.resultAt(i) != null)
                                {
                                    var savedProvision = (JsonArray) savedProvisions.resultAt(i);
                                    provisionArray.add(savedProvision.getJsonObject(0));
                                }
                            }
                            MonitorCache.insertMonitorArray(provisionArray);
                            HttpResponse.sendSuccess(ctx, 200, "Provisioned All Valid Ips", provisionArray);
                        }
                        else
                        {
                            HttpResponse.sendFailure(ctx, 500, "Error during provisioning", joinAr.cause().getMessage());
                        }
                    });
                }
            }
            else
            {
                HttpResponse.sendFailure(ctx, 400, "Discovery Not Found", "");
            }
        });
    }

    public static void deleteProvision(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id));
        checkRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var provision = ar.result();
                if (provision.isEmpty())
                {
                    HttpResponse.sendFailure(ctx, 404, "Provision not found");
                    return;
                }

                var deleteRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.DELETE, new JsonArray().add(id));
                deleteRequest.onComplete(delAr ->
                {
                    if (delAr.succeeded())
                    {
                        var deletedMonitor = delAr.result();
                        if (!deletedMonitor.isEmpty())
                        {
                            MonitorCache.deleteMetricGroups(deletedMonitor.getJsonObject(0).getInteger(Fields.MetricGroup.ID));
                            HttpResponse.sendSuccess(ctx, 200, "Provision deleted successfully", provision);
                        }
                        else
                        {
                            HttpResponse.sendFailure(ctx, 400, "Failed to delete Provision");
                        }
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", delAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Something Went Wrong", ar.cause().getMessage());
            }
        });
    }

    public static void updateMetric(RoutingContext ctx)
    {
        var id = Integer.parseInt(ctx.request().getParam("id"));
        var metrics = ctx.body().asJsonObject().getJsonArray("metrics");
        var checkRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id));
        checkRequest.compose(v ->
        {
            if (v.isEmpty())
            {
                HttpResponse.sendFailure(ctx, 404, "Monitor with id " + id + " Not exist");
                return Future.failedFuture("Monitor with id " + id + " Not exist");
            }

            var updateMetricGroupsFuture = new ArrayList<Future<JsonArray>>();
            for (var i = 0; i < metrics.size(); i++)
            {
                var name = metrics.getJsonObject(i).getString(Fields.MetricGroup.NAME);
                var pollingInterval = metrics.getJsonObject(i).getInteger(Fields.MetricGroup.POLLING_INTERVAL);
                var isEnabled = metrics.getJsonObject(i).getBoolean(Fields.MetricGroup.IS_ENABLED);
                updateMetricGroupsFuture.add(DbEventBus.sendQueryExecutionRequest(Queries.Monitor.UPDATE, new JsonArray().add(id).add(pollingInterval).add(name).add(isEnabled)));
            }

            return Future.join(updateMetricGroupsFuture);
        }).onComplete(ar ->
        {
            if (ar.succeeded())
            {
                var getRequest = DbEventBus.sendQueryExecutionRequest(Queries.Monitor.GET_BY_ID, new JsonArray().add(id));
                getRequest.onComplete(getAr ->
                {
                    if (getAr.succeeded())
                    {
                        var res = getAr.result();
                        MonitorCache.updateMetricGroups(res.getJsonObject(0).getJsonArray(Fields.Monitor.METRIC_GROUP_JSON));
                        HttpResponse.sendSuccess(ctx, 200, "Updated Provision", res);
                    }
                    else
                    {
                        HttpResponse.sendFailure(ctx, 500, "Failed To Update Discovery", getAr.cause().getMessage());
                    }
                });
            }
            else
            {
                HttpResponse.sendFailure(ctx, 500, "Error Updating Metric groups", ar.cause().getMessage());
            }
        });
    }
}
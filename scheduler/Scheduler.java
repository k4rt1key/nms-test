package org.nms.scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.cache.MonitorCache;
import org.nms.Logger;
import org.nms.constants.Config;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Scheduler extends AbstractVerticle
{
    private static final int CHECKING_INTERVAL = 30; // seconds

    private long timerId = 0L;

    @Override
    public void start(Promise<Void> startPromise)
    {
        var populateRequest = MonitorCache.populate();

        populateRequest.onComplete(ar ->
        {
            if (ar.succeeded())
            {
                timerId = App.vertx.setPeriodic(CHECKING_INTERVAL * 1000, id -> processMetricGroups());

                Logger.debug("✅ Scheduler Verticle Deployed with CHECKING_INTERVAL => " + CHECKING_INTERVAL + " seconds, on thread [ " + Thread.currentThread().getName() + " ] ");

                startPromise.complete();
            }
            else
            {
                startPromise.fail("❌ Error Deploying Scheduler => " + ar.cause().getMessage());
            }
        });
    }

    @Override
    public void stop()
    {
        if (timerId != 0)
        {
            vertx.cancelTimer(timerId);
            Logger.debug("\uD83D\uDED1 Scheduler Stopped");
            timerId = 0;
        }
    }

    private void processMetricGroups()
    {
        var timedOutGroups = MonitorCache.decrementAndCollectTimedOutMetricGroups(CHECKING_INTERVAL);

        if (!timedOutGroups.isEmpty())
        {
            var metricGroups = preparePollingMetricGroups(timedOutGroups);
            var pluginRequest = new JsonObject()
                    .put("type", "polling")
                    .put(Fields.PluginPollingRequest.METRIC_GROUPS, metricGroups);

            var POLLING_TIMEOUT = Config.INITIAL_PLUGIN_OVERHEAD_TIME + (metricGroups.size() * Config.POLLING_TIMEOUT_PER_METRIC_GROUP);

            vertx.eventBus().request(
                    Fields.EventBus.PLUGIN_ADDRESS,
                    pluginRequest,
                    new DeliveryOptions().setSendTimeout(POLLING_TIMEOUT * 1000L),
                    ar ->
                    {
                        if (ar.succeeded())
                        {
                            var response = (JsonObject) ar.result().body();
                            if (response.containsKey("error"))
                            {
                                Logger.error("❌ Error During Polling => " + response.getString("error"));
                            }
                            else
                            {
                                var results = response.getJsonArray(Fields.PluginPollingRequest.METRIC_GROUPS, new JsonArray());
                                processAndSaveResults(results);
                            }
                        }
                        else
                        {
                            Logger.error("❌ Error During Polling => " + ar.cause().getMessage());
                        }
                    }
            );
        }
    }

    private JsonArray preparePollingMetricGroups(List<JsonObject> timedOutGroups)
    {
        var metricGroups = new JsonArray();

        for (var metricGroup : timedOutGroups)
        {
            var groupData = new JsonObject()
                    .put(Fields.PluginPollingRequest.MONITOR_ID,
                            metricGroup.getInteger(Fields.MonitorCache.MONITOR_ID))
                    .put(Fields.PluginPollingRequest.NAME,
                            metricGroup.getString(Fields.MonitorCache.NAME))
                    .put(Fields.PluginPollingRequest.IP,
                            metricGroup.getString(Fields.MonitorCache.IP))
                    .put(Fields.PluginPollingRequest.PORT,
                            metricGroup.getInteger(Fields.MonitorCache.PORT))
                    .put(Fields.PluginPollingRequest.CREDENTIALS,
                            metricGroup.getJsonObject(Fields.MonitorCache.CREDENTIAL));
            metricGroups.add(groupData);
        }

        return metricGroups;
    }

    private void processAndSaveResults(JsonArray results)
    {
        var batchParams = new ArrayList<Tuple>();

        for (var i = 0; i < results.size(); i++)
        {
            var result = results.getJsonObject(i);

            if (!result.getBoolean("success", false))
            {
                continue;
            }

            result.put(Fields.PollingResult.TIME, ZonedDateTime.now().toString());
            var data = result.getString(Fields.PluginPollingResponse.DATA);

            if (isValidJsonObject(data))
            {
                batchParams.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),
                        result.getString(Fields.PluginPollingResponse.NAME),
                        new JsonObject(data)
                ));
            }
            else if (isValidJsonArray(data))
            {
                batchParams.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),
                        result.getString(Fields.PluginPollingResponse.NAME),
                        new JsonArray(data)
                ));
            }
            else
            {
                batchParams.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),
                        result.getString(Fields.PluginPollingResponse.NAME),
                        data
                ));
            }
        }

        if (!batchParams.isEmpty())
        {
            var saveRequest = DbEventBus.sendQueryExecutionRequest(Queries.PollingResult.INSERT, batchParams);
            saveRequest.onComplete(ar ->
            {
                if (ar.failed())
                {
                    Logger.error("❌ Error During Saving Polled Data => " + ar.cause().getMessage());
                }
            });
        }
    }

    private boolean isValidJsonObject(String s)
    {
        try
        {
            new JsonObject(s);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private boolean isValidJsonArray(String s)
    {
        try
        {
            new JsonArray(s);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
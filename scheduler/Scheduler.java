package org.nms.scheduler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.cache.MonitorCache;
import org.nms.ConsoleLogger;
import org.nms.constants.Config;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Scheduler extends AbstractVerticle
{
    private static final int CHECKING_INTERVAL = 30; // seconds

    private long timerId;

    @Override
    public void start()
    {
        ConsoleLogger.debug("✅ Starting SchedulerVerticle with CHECKING_INTERVAL => " + CHECKING_INTERVAL + " seconds, on thread [ " + Thread.currentThread().getName() + " ] ");

        // Populate cache from DB
        MonitorCache.populate()
                // Start periodic timer for checking metric groups
                .onSuccess(res ->
                        timerId = App.vertx.setPeriodic(CHECKING_INTERVAL * 1000, id -> processMetricGroups()))
                .onFailure(err ->
                        ConsoleLogger.error("❌ Error Running Scheduler => " + err.getMessage())
                );
    }

    @Override
    public void stop()
    {
        if (timerId != 0)
        {
            vertx.cancelTimer(timerId);

            ConsoleLogger.debug("\uD83D\uDED1 Scheduler Stopped");

            timerId = 0;
        }
    }

    // Process metric groups and handle polling
    private void processMetricGroups()
    {
        // Decrement intervals and collect timed-out groups
        var timedOutGroups = MonitorCache.decrementAndCollectTimedOutMetricGroups(CHECKING_INTERVAL);

        // If there are timed-out groups, process them
        if (!timedOutGroups.isEmpty())
        {
            // Prepare polling request for Plugin Manager
            var metricGroups = preparePollingMetricGroups(timedOutGroups);

            // Create proper plugin request
            var pluginRequest = new JsonObject()
                    .put("type", "polling")
                    .put(Fields.PluginPollingRequest.METRIC_GROUPS, metricGroups);

            var POLLING_TIMEOUT = Config.INITIAL_OVERHEAD + (metricGroups.size() * Config.POLLING_TIMEOUT_PER_METRIC_GROUP);

            // Send polling request via plugin execute event bus
            vertx.eventBus().request(
                    "plugin.execute",
                    pluginRequest,
                    new DeliveryOptions().setSendTimeout(POLLING_TIMEOUT * 1000L),
                    ar ->
                    {
                        if (ar.succeeded())
                        {
                            var response = (JsonObject) ar.result().body();

                            if (response.containsKey("error"))
                            {
                                ConsoleLogger.error("❌ Error During Polling => " + response.getString("error"));
                            }
                            else
                            {
                                var results = response.getJsonArray(Fields.PluginPollingRequest.METRIC_GROUPS, new JsonArray());
                                processAndSaveResults(results);
                            }
                        }
                        else
                        {
                            ConsoleLogger.error("❌ Error During Polling => " + ar.cause().getMessage());
                        }
                    }
            );
        }
    }

    // Prepare metric groups for polling
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

    // Process and save polling results
    private void processAndSaveResults(JsonArray results)
    {
        var batchParams = new ArrayList<Tuple>();

        for (var i = 0; i < results.size(); i++)
        {
            var result = results.getJsonObject(i);

            // Skip unsuccessful results
            if (!result.getBoolean("success", false))
            {
                continue;
            }

            // Add timestamp
            result.put(Fields.PollingResult.TIME, ZonedDateTime.now().toString());

            // Handle different data types
            var data = result.getString(Fields.PluginPollingResponse.DATA);

            // Prepare batch parameter based on data type
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

        // Save results to database
        if (!batchParams.isEmpty())
        {
            DbEngine.execute(Queries.PollingResult.INSERT, batchParams)
                    .onFailure(err ->
                            ConsoleLogger.error("❌ Error During Saving Polled Data => " + err.getMessage())
                    );
        }
    }

    // Utility methods to validate JSON
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
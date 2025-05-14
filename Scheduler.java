package org.nms;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;

import static org.nms.App.logger;
import org.nms.constants.Config;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbUtility;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Scheduler extends AbstractVerticle
{
    private long timerId = 0L;

    @Override
    public void start(Promise<Void> startPromise)
    {
        // Populate cache
        var populateCache = Cache.populate();

        populateCache.onComplete(populateCacheResult ->
        {
            if (populateCacheResult.succeeded())
            {
                // Start scheduler
                timerId = App.vertx.setPeriodic(Config.SCHEDULER_CHECKING_INTERVAL * 1000, id -> pollTimedOutGroups());

                logger.info("✅ Scheduler Verticle deployed with CHECKING_INTERVAL: " + Config.SCHEDULER_CHECKING_INTERVAL + " seconds, on thread [ " + Thread.currentThread().getName() + " ] ");

                startPromise.complete();
            }
            else
            {
                startPromise.fail("❌ Error deploying scheduler: " + populateCacheResult.cause().getMessage());
            }
        });
    }

    @Override
    public void stop()
    {
        vertx.cancelTimer(timerId);

        logger.debug("⚠ Scheduler stopped");
    }

    private void pollTimedOutGroups()
    {
        var timedOutGroups = Cache.collectTimedOutGroups(Config.SCHEDULER_CHECKING_INTERVAL * 1000);

        if (!timedOutGroups.isEmpty())
        {
            var request = buildPollingRequest(timedOutGroups);

            var POLLING_TIMEOUT = Config.BASE_TIME + ( timedOutGroups.size() * Config.POLLING_TIMEOUT_PER_METRIC_GROUP );

            // Send payload to plugin
            vertx.eventBus().<JsonObject>request(
                    Fields.EventBus.PLUGIN_SPAWN_ADDRESS,
                    request,
                    new DeliveryOptions().setSendTimeout(POLLING_TIMEOUT * 1000L),
                    pluginResponse ->
                    {
                        if (pluginResponse.succeeded())
                        {
                            var response = pluginResponse.result().body();

                            if (!response.isEmpty())
                            {
                                var results = response.getJsonArray(Fields.PluginPollingRequest.METRIC_GROUPS, new JsonArray());

                                savePollingResults(results);
                            }
                        }
                        else
                        {
                            logger.error("❌ Error During Polling: " + pluginResponse.cause().getMessage());
                        }
                    }
            );
        }
    }

    private JsonObject buildPollingRequest(List<JsonObject> timedOutGroups)
    {
        var metricGroups = new JsonArray();

        for (var metricGroup : timedOutGroups)
        {
            var groupData = new JsonObject()

                    .put(Fields.PluginPollingRequest.MONITOR_ID, metricGroup.getInteger(Fields.MonitorCache.MONITOR_ID))

                    .put(Fields.PluginPollingRequest.NAME, metricGroup.getString(Fields.MonitorCache.NAME))

                    .put(Fields.PluginPollingRequest.IP, metricGroup.getString(Fields.MonitorCache.IP))

                    .put(Fields.PluginPollingRequest.PORT, metricGroup.getInteger(Fields.MonitorCache.PORT))

                    .put(Fields.PluginPollingRequest.CREDENTIALS, metricGroup.getJsonObject(Fields.MonitorCache.CREDENTIAL));

            metricGroups.add(groupData);
        }

        return new JsonObject()

                .put(Fields.PluginPollingRequest.TYPE, Fields.PluginPollingRequest.POLLING)

                .put(Fields.PluginPollingRequest.METRIC_GROUPS, metricGroups);
    }

    private void savePollingResults(JsonArray results)
    {
        var insertValuesBatch = new ArrayList<Tuple>();

        for (var i = 0; i < results.size(); i++)
        {
            var result = results.getJsonObject(i);

            if (!result.getBoolean("success", false))
            {
                continue;
            }

            result.put(Fields.PollingResult.TIME, ZonedDateTime.now(ZoneId.of(Config.INDIA_ZONE_NAME)).toString());

            // Polled data as string
            var polledData = result.getString(Fields.PluginPollingResponse.DATA);

            // Is Polled data parsable as JsonObject
            if (isParsableAsJsonObject(polledData))
            {
                insertValuesBatch.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),

                        result.getString(Fields.PluginPollingResponse.NAME),

                        new JsonObject(polledData)
                ));
            }
            // Or Polled data is parsable as JsonArray
            else if (isParsableAsJsonArray(polledData))
            {
                insertValuesBatch.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),

                        result.getString(Fields.PluginPollingResponse.NAME),

                        new JsonArray(polledData)
                ));
            }
            // Otherwise keep it as string
            else
            {
                insertValuesBatch.add(Tuple.of(
                        result.getInteger(Fields.PluginPollingResponse.MONITOR_ID),

                        result.getString(Fields.PluginPollingResponse.NAME),

                        polledData
                ));
            }
        }

        if (!insertValuesBatch.isEmpty())
        {
            var saveRequest = DbUtility.sendQueryExecutionRequest(Queries.PollingResult.INSERT, insertValuesBatch);

            saveRequest.onComplete(insertInDbResult ->
            {
                if (insertInDbResult.failed())
                {
                    logger.error("❌ Error during inserting polled data: " + insertInDbResult.cause().getMessage());
                }
            });
        }
    }

    private boolean isParsableAsJsonObject(String s)
    {
        try
        {
            new JsonObject(s);
            return true;
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    private boolean isParsableAsJsonArray(String s)
    {
        try
        {
            new JsonArray(s);

            return true;
        }
        catch (Exception exception)
        {
            return false;
        }
    }
}
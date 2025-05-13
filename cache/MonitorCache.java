package org.nms.cache;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.nms.ConsoleLogger;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.DbEngine;

public class MonitorCache
{
    private static final ConcurrentHashMap<Integer, JsonObject> cachedMetricGroups = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, JsonObject> referencedMetricGroups = new ConcurrentHashMap<>();

    // Populate cache from database
    public static Future<JsonArray> populate()
    {
        try
        {
            return DbEngine.execute(Queries.Monitor.GET_ALL)
                    .onSuccess(monitorArray ->
                    {
                        if (!monitorArray.isEmpty())
                        {
                            insertMonitorArray(monitorArray);
                        }
                    });
        }
        catch (Exception e)
        {
            ConsoleLogger.error("Error Populating Cache");
            return Future.failedFuture("Error Populating Cache");
        }
    }

    // Insert monitors into cache
    public static void insertMonitorArray(JsonArray monitorArray)
    {
        for (var i = 0; i < monitorArray.size(); i++)
        {
            var monitorObject = monitorArray.getJsonObject(i);

            for (var k = 0; k < monitorObject.getJsonArray(Fields.Monitor.METRIC_GROUP_JSON).size(); k++)
            {
                try
                {
                    var metricObject = monitorObject.getJsonArray(Fields.Monitor.METRIC_GROUP_JSON).getJsonObject(k);

                    var value = new JsonObject()
                            .put(Fields.MonitorCache.ID, metricObject.getInteger(Fields.MetricGroup.ID))
                            .put(Fields.MonitorCache.MONITOR_ID, monitorObject.getInteger(Fields.Monitor.ID))
                            .put(Fields.MonitorCache.IP, monitorObject.getString(Fields.Monitor.IP))
                            .put(Fields.MonitorCache.PORT, Integer.valueOf(monitorObject.getString(Fields.Monitor.PORT)))
                            .put(Fields.MonitorCache.CREDENTIAL, monitorObject.getJsonObject(Fields.Monitor.CREDENTIAL_JSON).copy())
                            .put(Fields.MonitorCache.NAME, metricObject.getString(Fields.MetricGroup.NAME))
                            .put(Fields.MonitorCache.POLLING_INTERVAL, metricObject.getInteger(Fields.MetricGroup.POLLING_INTERVAL))
                            .put(Fields.MonitorCache.IS_ENABLED, metricObject.getBoolean(Fields.MetricGroup.IS_ENABLED));

                    var key = metricObject.getInteger(Fields.MetricGroup.ID);

                    referencedMetricGroups.put(key, value.copy());
                    cachedMetricGroups.put(key, value.copy());
                }
                catch (Exception e)
                {
                    ConsoleLogger.warn("Error Inserting Monitor in cache: " + e.getMessage());
                }
            }
        }


        ConsoleLogger.info("📬 Inserted " + monitorArray.size() + " monitors Into Cache, Total Entries: " + cachedMetricGroups.size());
    }

    // Update metric groups in cache
    public static void updateMetricGroups(JsonArray metricGroups)
    {
        for (var i = 0; i < metricGroups.size(); i++)
        {
            try
            {
                var metricGroup = metricGroups.getJsonObject(i);

                var key = metricGroup.getInteger(Fields.MonitorCache.ID);

                var updatedValue = referencedMetricGroups.get(key).copy();

                // Update polling interval if provided
                if (metricGroup.getValue(Fields.MonitorCache.POLLING_INTERVAL) != null)
                {
                    updatedValue.put(Fields.MonitorCache.POLLING_INTERVAL,
                            metricGroup.getInteger(Fields.MonitorCache.POLLING_INTERVAL));
                }

                // Remove if disabled
                if (metricGroup.getBoolean(Fields.MonitorCache.IS_ENABLED) != null
                        && !metricGroup.getBoolean(Fields.MonitorCache.IS_ENABLED))
                {
                    referencedMetricGroups.remove(key);
                    cachedMetricGroups.remove(key);
                    continue;
                }

                // Update both caches
                referencedMetricGroups.put(key, updatedValue);
                cachedMetricGroups.put(key, updatedValue);
            }
            catch (Exception e)
            {
                ConsoleLogger.warn("Error Updating Monitor in Cache: " + e.getMessage());
            }
        }

        ConsoleLogger.info("➖ Updated " + metricGroups.size() + " Entries in Cache");
    }

    // Delete metric groups for a specific monitor
    public static void deleteMetricGroups(Integer monitorId)
    {
        var removedCount = new ArrayList<Integer>();

        referencedMetricGroups.entrySet().removeIf(entry ->
        {
            if (entry.getValue().getInteger(Fields.MonitorCache.MONITOR_ID).equals(monitorId))
            {
                cachedMetricGroups.remove(entry.getKey());
                removedCount.add(entry.getKey());
                return true;
            }
            return false;
        });

        ConsoleLogger.info("➖ Removed " + removedCount.size() + " Entries from Cache");
    }

    // Decrement intervals and collect timed-out metric groups
    public static List<JsonObject> decrementAndCollectTimedOutMetricGroups(int interval)
    {
        var timedOutMetricGroups = new ArrayList<JsonObject>();

        // Collect and process timed-out groups in a single pass
        cachedMetricGroups.entrySet().removeIf(entry ->
        {
            var value = entry.getValue();
            var currentInterval = value.getInteger(Fields.MonitorCache.POLLING_INTERVAL, 0);
            var updatedInterval = Math.max(0, currentInterval - interval);

            // If interval reaches 0, it's timed out
            if (updatedInterval == 0)
            {
                // Create a copy of the timed-out group
                var timedOutGroup = value.copy();

                // Reset interval to original value from reference
                var originalIntervalEntry = referencedMetricGroups
                        .get(entry.getKey())
                        .getInteger(Fields.MonitorCache.POLLING_INTERVAL);

                timedOutGroup.put(Fields.MonitorCache.POLLING_INTERVAL, originalIntervalEntry);

                // Add to timed-out groups
                timedOutMetricGroups.add(timedOutGroup);

                // Update the cached value with reset interval
                value.put(Fields.MonitorCache.POLLING_INTERVAL, originalIntervalEntry);
            }
            else
            {
                // Update interval in cached value
                value.put(Fields.MonitorCache.POLLING_INTERVAL, updatedInterval);
            }

            // return false to keep all entries
            return false;
        });

        ConsoleLogger.debug("⏰ Found " + timedOutMetricGroups.size() + " Timed Out Metric Groups");
        return timedOutMetricGroups;
    }
}
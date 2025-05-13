package org.nms.plugin;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.nms.Logger;
import org.nms.constants.Config;
import org.nms.constants.Fields;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Plugin extends AbstractVerticle
{
    @Override
    public void start()
    {
        vertx.eventBus().localConsumer(Fields.EventBus.PLUGIN_ADDRESS, message ->
        {
            var request = (JsonObject) message.body();
            executePlugin(request)
                    .onComplete(ar -> message.reply(ar.result()));
        });

        Logger.info("✅ Plugin Verticle Deployed");
    }


    @Override
    public void stop()
    {
        Logger.info("\uD83D\uDED1 Plugin Verticle Stopped");
    }

    /**
     * Core method to execute the plugin process with a JSON request
     * @param request The JSON request to send to the plugin
     * @return Future with the plugin response as JsonObject
     */
    private Future<JsonObject> executePlugin(JsonObject request)
    {
        return vertx.executeBlocking(() ->
        {
            try
            {
                // Calculate timeout based on request type
                var timeout = calculateTimeout(request);

                // Prepare command
                var inputJsonStr = request.encode();
                var command = new String[] {Config.PLUGIN_PATH, inputJsonStr};

                Logger.debug("Executing plugin with request: " + inputJsonStr);

                // Run command
                var builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                var process = builder.start();

                // Wait for response with timeout
                var done = process.waitFor(timeout, TimeUnit.SECONDS);

                if (!done)
                {
                    Logger.warn("Plugin not responding within " + timeout + " seconds");
                    process.destroyForcibly();
                    return new JsonObject().put("error", "Plugin execution timed out");
                }

                // Read output
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = reader.lines().collect(Collectors.joining());

                // Parse response
                return new JsonObject(output);
            }
            catch (Exception e)
            {
                Logger.error("Error executing plugin: " + e.getMessage());
                return new JsonObject().put("error", "Plugin execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * Calculate timeout based on request type and content
     */
    private int calculateTimeout(JsonObject request)
    {
        var type = request.getString("type", "");

        if ("discovery".equals(type))
        {
            var ips = request.getJsonArray(Fields.PluginDiscoveryRequest.IPS, null);
            var ipsCount = (ips != null) ? ips.size() : 0;
            return Config.INITIAL_PLUGIN_OVERHEAD_TIME + (ipsCount * Config.DISCOVERY_TIMEOUT_PER_IP);
        }
        else if ("polling".equals(type))
        {
            var metricGroups = request.getJsonArray(Fields.PluginPollingRequest.METRIC_GROUPS, null);
            var metricGroupCount = (metricGroups != null) ? metricGroups.size() : 0;
            return Config.INITIAL_PLUGIN_OVERHEAD_TIME + (metricGroupCount * Config.POLLING_TIMEOUT_PER_METRIC_GROUP);
        }

        // Default timeout
        return Config.INITIAL_PLUGIN_OVERHEAD_TIME + 10;
    }
}
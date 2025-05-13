package org.nms.discovery;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.ConsoleLogger;
import org.nms.constants.Fields;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Discovery extends AbstractVerticle
{
    private static final int PORT_CONNECT_TIMEOUT = 2000;
    private static final int TIMEOUT_SECONDS = 30;

    @Override
    public void start()
    {
        // Ping Check Handler
        vertx.eventBus().consumer(Fields.EventBus.PING_CHECK_ADDRESS, message ->
        {
            var body = (JsonObject) message.body();

            pingIps(body.getJsonArray(Fields.PluginDiscoveryRequest.IPS))
                    .onComplete(ar -> message.reply(ar.result()));
        });

        // Port Check Handler
        vertx.eventBus().consumer(Fields.EventBus.PORT_CHECK_ADDRESS, message ->
        {
            var body = (JsonObject) message.body();

            checkPorts(
                    body.getJsonArray(Fields.PluginDiscoveryRequest.IPS),
                    body.getInteger(Fields.PluginDiscoveryRequest.PORT)
            )
                    .onComplete(ar -> message.reply(ar.result()));
        });

        // Discovery Handler - Now uses the plugin.execute event bus
        vertx.eventBus().consumer(Fields.EventBus.DISCOVERY_ADDRESS, message ->
        {
            var body = (JsonObject) message.body();

            // Create a properly formatted request for the plugin
            var pluginRequest = new JsonObject()
                    .put("type", "discovery")
                    .put("id", body.getInteger(Fields.PluginDiscoveryRequest.ID))
                    .put("ips", body.getJsonArray(Fields.PluginDiscoveryRequest.IPS))
                    .put("port", body.getInteger(Fields.PluginDiscoveryRequest.PORT))
                    .put("credentials", body.getJsonArray(Fields.PluginDiscoveryRequest.CREDENTIALS));

            // Send request to plugin manager
            vertx.eventBus().request(Fields.EventBus.PLUGIN_ADDRESS, pluginRequest, pluginReply ->
            {
                if (pluginReply.succeeded())
                {
                    var response = (JsonObject) pluginReply.result().body();
                    if (response.containsKey("error"))
                    {
                        ConsoleLogger.error("Error from plugin: " + response.getString("error"));
                        message.reply(new JsonArray());
                    }
                    else
                    {
                        message.reply(response.getJsonArray("result", new JsonArray()));
                    }
                }
                else
                {
                    ConsoleLogger.error("Error calling plugin: " + pluginReply.cause().getMessage());
                    message.reply(new JsonArray());
                }
            });
        });
    }

    private Future<JsonArray> pingIps(JsonArray ipArray)
    {
        return vertx.executeBlocking(promise ->
        {
            var results = new JsonArray();
            Process process = null;
            BufferedReader reader = null;

            try
            {
                // Validate input
                if (ipArray == null || ipArray.isEmpty())
                {
                    promise.complete(results);
                    return;
                }

                // Collect valid IPs
                var validIps = new JsonArray();
                for (var i = 0; i < ipArray.size(); i++)
                {
                    var ipObj = ipArray.getValue(i);

                    if (!(ipObj instanceof String))
                    {
                        continue;
                    }

                    var ip = ((String) ipObj).trim();
                    if (ip.isEmpty())
                    {
                        continue;
                    }

                    validIps.add(ip);
                }

                if (validIps.isEmpty())
                {
                    promise.complete(results);
                    return;
                }

                // Prepare fping command
                var command = new String[validIps.size() + 2];
                command[0] = "fping";
                command[1] = "-c3";

                for (var i = 0; i < validIps.size(); i++)
                {
                    command[i + 2] = validIps.getString(i);
                }

                var pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                process = pb.start();

                // Process timeout
                var completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (!completed)
                {
                    process.destroyForcibly();
                    promise.complete(createErrorResultForAll(ipArray, "Ping process timed out"));
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                var processedIps = new JsonArray();

                while ((line = reader.readLine()) != null)
                {
                    var parts = line.split(":");
                    if (parts.length < 2)
                    {
                        continue;
                    }

                    var ip = parts[0].trim();
                    var stats = parts[1].trim();
                    processedIps.add(ip);

                    var result = new JsonObject()
                            .put("success", !stats.contains("100%"))
                            .put("ip", ip)
                            .put("message",
                                    stats.contains("100%")
                                            ? "Ping check failed: 100% packet loss"
                                            : "Ping check success (avg latency: " + extractAvgLatency(stats) + ")");

                    results.add(result);
                }

                // Handle unprocessed IPs
                for (var i = 0; i < validIps.size(); i++)
                {
                    var ip = validIps.getString(i);
                    if (!processedIps.contains(ip))
                    {
                        results.add(createErrorResult(ip, "No response from fping"));
                    }
                }

                promise.complete(results);
            }
            catch (Exception e)
            {
                ConsoleLogger.error("Error during ping check: " + e.getMessage());
                promise.complete(createErrorResultForAll(ipArray, "Error during ping check"));
            }
            finally
            {
                closeQuietly(reader);
                if (process != null && process.isAlive())
                {
                    try
                    {
                        process.destroyForcibly();
                    }
                    catch (Exception e)
                    {
                        ConsoleLogger.error("Error destroying ping process: " + e.getMessage());
                    }
                }
            }
        });
    }

    private Future<JsonArray> checkPorts(JsonArray ips, int port)
    {
        var promise = Promise.<JsonArray>promise();
        var results = new JsonArray();
        var futures = new ArrayList<Future>();

        if (ips == null || ips.isEmpty() || port < 1 || port > 65535)
        {
            promise.complete(results);
            return promise.future();
        }

        for (var ipObj : ips)
        {
            if (!(ipObj instanceof String))
            {
                continue;
            }

            var ip = ((String) ipObj).trim();
            if (ip.isEmpty())
            {
                continue;
            }

            var checkPromise = Promise.<JsonObject>promise();
            futures.add(checkPromise.future());

            var result = new JsonObject()
                    .put("ip", ip)
                    .put("port", port);

            try
            {
                vertx.createNetClient()
                        .connect(port, ip, ar ->
                        {
                            try
                            {
                                if (ar.succeeded())
                                {
                                    var socket = ar.result();
                                    result.put("success", true)
                                            .put("message", "Port " + port + " is open on " + ip);
                                    socket.close();
                                }
                                else
                                {
                                    var cause = ar.cause();
                                    var errorMessage = cause != null ? cause.getMessage() : "Unknown error";

                                    result.put("success", false)
                                            .put("message",
                                                    errorMessage.contains("Connection refused")
                                                            ? "Port " + port + " is closed on " + ip
                                                            : errorMessage);
                                }
                            }
                            catch (Exception e)
                            {
                                result.put("success", false)
                                        .put("message", "Error handling connection: " + e.getMessage());
                            }
                            finally
                            {
                                results.add(result);
                                checkPromise.complete(result);
                            }
                        });
            }
            catch (Exception e)
            {
                result
                        .put("success", false)
                        .put("message", "Error creating connection: " + e.getMessage());
                results
                        .add(result);

                checkPromise.complete(result);
            }
        }

        CompositeFuture.all(futures)
                .onComplete(ar ->
                {
                    if (ar.succeeded())
                    {
                        promise.complete(results);
                    }
                    else
                    {
                        ConsoleLogger.error("Error in port check: " + ar.cause().getMessage());
                        promise.complete(results);
                    }
                });

        return promise.future();
    }

    private String extractAvgLatency(String stats)
    {
        try
        {
            if (stats.contains("min/avg/max"))
            {
                var latencyParts = stats.split("min/avg/max = ");
                if (latencyParts.length == 2)
                {
                    var latencyValues = latencyParts[1].split("/");
                    if (latencyValues.length >= 2)
                    {
                        return latencyValues[1] + " ms";
                    }
                }
            }
        }
        catch (Exception e)
        {
            ConsoleLogger.warn("Error extracting latency: " + e.getMessage());
        }
        return "";
    }

    private JsonObject createErrorResult(String ip, String message)
    {
        return new JsonObject()
                .put("ip", ip)
                .put("success", false)
                .put("message", message);
    }

    private JsonArray createErrorResultForAll(JsonArray ipArray, String message)
    {
        var results = new JsonArray();
        if (ipArray != null)
        {
            for (var i = 0; i < ipArray.size(); i++)
            {
                var ip = String.valueOf(ipArray.getValue(i));
                results.add(createErrorResult(ip, message));
            }
        }
        return results;
    }

    private void closeQuietly(AutoCloseable closeable)
    {
        if (closeable != null)
        {
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                ConsoleLogger.error("Error closing resource: " + e.getMessage());
            }
        }
    }
}
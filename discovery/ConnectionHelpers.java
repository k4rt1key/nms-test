package org.nms.discovery;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClientOptions;
import org.nms.App;

import static org.nms.App.logger;

import org.nms.constants.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.nms.constants.Fields.Discovery.*;
import static org.nms.constants.Fields.DiscoveryResult.MESSAGE;
import static org.nms.discovery.DiscoveryHelpers.*;

public class ConnectionHelpers
{
    public static Future<JsonArray> pingIps(JsonArray ips)
    {
        return App.vertx.executeBlocking(promise ->
        {
            var results = new JsonArray();

            Process process = null;

            BufferedReader reader = null;

            try
            {
                // Validate input
                if (ips == null || ips.isEmpty())
                {
                    promise.complete(results);

                    return;
                }

                // Prepare fping command
                var command = new String[ips.size() + 3];

                command[0] = "fping";

                command[1] = "-c1";

                command[2] = "-q";

                for (var i = 0; i < ips.size(); i++)
                {
                    command[i + 3] = ips.getString(i);
                }

                var processBuilder = new ProcessBuilder(command);

                processBuilder.redirectErrorStream(true);

                process = processBuilder.start();

                // Process timeout
                var completed = process.waitFor(Config.BASE_TIME + (Config.DISCOVERY_TIMEOUT_PER_IP * ips.size()), TimeUnit.SECONDS);

                if (!completed)
                {
                    process.destroyForcibly();

                    promise.complete(createErrorResultForAll(ips, "Ping process timed out"));

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

                    logger.debug("procced ip " + ip);

                    var isSuccess = !stats.contains("100%");

                    var result = new JsonObject()
                            .put(SUCCESS, isSuccess)
                            .put(IP, ip)
                            .put(MESSAGE,
                                    isSuccess
                                            ? "Ping check success"
                                            : "Ping check failed: 100% packet loss");

                    results.add(result);
                }

                // Handle unprocessed IPs
                for (var i = 0; i < ips.size(); i++)
                {
                    var ip = ips.getString(i);

                    if (!processedIps.contains(ip))
                    {
                        results.add(createErrorResult(ip, "No response from fping"));
                    }
                }

                promise.complete(results);
            }
            catch (Exception exception)
            {
                logger.error("Error during ping check: " + exception.getMessage());

                promise.complete(createErrorResultForAll(ips, "Error during ping check"));
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
                    catch (Exception exception)
                    {
                        logger.error("Error destroying ping process: " + exception.getMessage());
                    }
                }
            }
        });
    }

    public static Future<JsonArray> checkPorts(JsonArray ips, int port)
    {
        var promise = Promise.<JsonArray>promise();

        var results = new JsonArray();

        var futures = new ArrayList<Future<JsonObject>>();

        if (ips == null || ips.isEmpty() || port < 1 || port > 65535)
        {
            promise.complete(results);

            return promise.future();
        }

        for (var ip : ips)
        {
            var checkPromise = Promise.<JsonObject>promise();

            futures.add(checkPromise.future());

            var result = new JsonObject()
                    .put(IP, ip.toString())
                    .put(PORT, port);

            try
            {
                App.vertx.createNetClient(new NetClientOptions().setConnectTimeout(Config.PORT_TIMEOUT * 1000))
                        .connect(port, ip.toString(), asyncResult ->
                        {
                            try
                            {
                                if (asyncResult.succeeded())
                                {
                                    var socket = asyncResult.result();

                                    result.put(SUCCESS, true)
                                            .put(MESSAGE, "Port " + port + " is open on " + ip);

                                    socket.close();
                                }
                                else
                                {
                                    var cause = asyncResult.cause();

                                    var errorMessage = cause != null ? cause.getMessage() : "Unknown error";

                                    result.put(SUCCESS, false)
                                            .put(MESSAGE,
                                                    errorMessage.contains("Connection refused")
                                                            ? "Port " + port + " is closed on " + ip
                                                            : errorMessage);
                                }
                            }
                            catch (Exception exception)
                            {
                                result
                                        .put(SUCCESS, false)
                                        .put(MESSAGE, "Something went wrong");

                                logger.error("Something went wrong, error: " + exception.getMessage());
                            }
                            finally
                            {
                                results.add(result);

                                checkPromise.complete(result);
                            }
                        });
            }
            catch (Exception exception)
            {
                result
                        .put(SUCCESS, false)
                        .put(MESSAGE, "Error creating connection: " + exception.getMessage());
                results
                        .add(result);

                checkPromise.complete(result);
            }
        }

        Future.all(futures)
                .onComplete(ar ->
                {
                    if (ar.succeeded())
                    {
                        promise.complete(results);
                    }
                    else
                    {
                        logger.error("Error in port check: " + ar.cause().getMessage());

                        promise.complete(results);
                    }
                });

        return promise.future();
    }
}



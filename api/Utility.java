package org.nms.api;

import inet.ipaddr.IPAddressString;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.constants.Config;
import static org.nms.App.logger;

public class Utility
{
    public static void sendSuccess(RoutingContext ctx, int statusCode, String message, JsonArray data)
    {
        var response = ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json");

        var json = new JsonObject()
                .put("success", true)
                .put("statusCode", statusCode)
                .put("message", message)
                .put("data", data);

        response.end(json.toBuffer());
    }

    public static void sendFailure(RoutingContext ctx, int statusCode, String message)
    {
        var response = ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json");

        var json = new JsonObject()
                .put("success", false)
                .put("statusCode", statusCode)
                .put("message", message);

        response.end(json.toBuffer());
    }

    public static void sendFailure(RoutingContext ctx, int statusCode, String message, String error)
    {
        var response = ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json");

        var json = new JsonObject()
                .put("success", false)
                .put("statusCode", statusCode)
                .put("message", message)
                .put("error", Config.PRODUCTION ? message : error);

        response.end(json.toBuffer());
    }

    public static JsonArray getIpsFromString(String ip, String ipType)
    {
        var ips = new JsonArray();

        if (Validators.validateIpWithIpType(ip, ipType))
        {
            return ips;
        }

        try
        {
            if ("RANGE".equalsIgnoreCase(ipType))
            {
                var parts = ip.split("-");

                var startIp = new IPAddressString(parts[0].trim()).getAddress();

                var endIp = new IPAddressString(parts[1].trim()).getAddress();

                var address = startIp.toSequentialRange(endIp);

                address.getIterable().forEach(ipAddress -> ips.add(ipAddress.toString()));
            }
            else if ("CIDR".equalsIgnoreCase(ipType))
            {
                new IPAddressString(ip)
                        .getSequentialRange()
                        .getIterable()
                        .forEach(ipAddress -> ips.add(ipAddress.toString()));
            }
            else if ("SINGLE".equalsIgnoreCase(ipType))
            {
                var singleAddr = new IPAddressString(ip).getAddress();

                ips.add(singleAddr.toString());
            }

            return ips;
        }
        catch (Exception exception)
        {
            logger.error("Failed to convert ip string to JsonArray of ip, error: " + exception.getMessage() );

            return new JsonArray();
        }
    }
}
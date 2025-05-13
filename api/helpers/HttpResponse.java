package org.nms.api.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.nms.constants.Config;

public class HttpResponse
{
    public static void sendSuccess(RoutingContext ctx, int statusCode, String message, JsonArray data)
    {
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(
                    new JsonObject()
                            .put("success", true)
                            .put("statusCode", statusCode)
                            .put("message", message)
                            .put("data", data)
                            .toBuffer()
            );
    }

    public static void sendFailure(RoutingContext ctx, int statusCode, String message)
    {
        ctx.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(
                    new JsonObject()
                            .put("success", false)
                            .put("statusCode", statusCode)
                            .put("message", message)
                            .toBuffer()
            );
    }

    public static void sendFailure(RoutingContext ctx, int statusCode, String message, String error)
    {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(
                        new JsonObject()
                                .put("success", false)
                                .put("statusCode", statusCode)
                                .put("message", message)
                                .put("error", Config.PRODUCTION ? message : error)
                                .toBuffer()
                );
    }
}

package org.nms.discovery.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.Logger;

public class Utils
{
    public static JsonObject createErrorResult(String ip, String message)
    {
        return new JsonObject()
                .put("ip", ip)
                .put("success", false)
                .put("message", message);
    }

    public static JsonArray createErrorResultForAll(JsonArray ipArray, String message)
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

    public static void closeQuietly(AutoCloseable closeable)
    {
        if (closeable != null)
        {
            try
            {
                closeable.close();
            }
            catch (Exception e)
            {
                Logger.error("Error closing resource: " + e.getMessage());
            }
        }
    }
}

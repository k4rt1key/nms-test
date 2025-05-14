package org.nms.discovery;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.nms.constants.Fields;

import static org.nms.App.logger;

public class DiscoveryHelpers
{
    public static JsonObject createErrorResult(String ip, String message)
    {
        return new JsonObject()
                .put(Fields.Discovery.IP, ip)
                .put(Fields.Discovery.SUCCESS, false)
                .put(Fields.DiscoveryResult.MESSAGE, message);
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
            catch (Exception exception)
            {
                logger.error("Error closing resource: " + exception.getMessage());
            }
        }
    }
}

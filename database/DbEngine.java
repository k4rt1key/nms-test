package org.nms.database;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.ConsoleLogger;
import org.nms.constants.Fields;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DbEngine
{
    public static Future<JsonArray> execute(String sql, JsonArray params)
    {
        JsonObject request = new JsonObject()
                .put("sql", sql)
                .put("params", params);

        return App.vertx.eventBus().<JsonArray>request(
                        Fields.EventBus.EXECUTE_SQL_WITH_PARAMS_ADDRESS,
                        request
                )

                .map(message ->
                {
                    ConsoleLogger.info("✅ Successfully received response for: \n🚀 " + sql);

                    return message.body();
                })

                .onFailure(err ->
                        ConsoleLogger.warn("❌ Failed to execute " + sql + ", error => " + err.getMessage())
                );
    }

    public static Future<JsonArray> execute(String sql)
    {
        return App.vertx.eventBus().<JsonArray>request(
                        Fields.EventBus.EXECUTE_SQL_ADDRESS,
                        sql
                )

                .map(message ->
                {
                    ConsoleLogger.info("✅ Successfully received response for: \n🚀 " + sql);

                    return message.body();
                })

                .onFailure(err ->
                        ConsoleLogger.warn("❌ Failed to execute " + sql + ", error => " + err.getMessage())
                );
    }

    public static Future<JsonArray> execute(String sql, List<Tuple> params)
    {

        // Convert List<Tuple> to JsonArray of JsonArrays for transmission over event bus
        JsonArray paramArrays = new JsonArray();

        for (Tuple tuple : params)
        {
            JsonArray paramArray = new JsonArray();

            // Convert each Tuple to JsonArray
            int size = tuple.size();

            for (int i = 0; i < size; i++)
            {
                paramArray.add(tuple.getValue(i));
            }

            paramArrays.add(paramArray);
        }

        JsonObject request = new JsonObject()
                .put("sql", sql)
                .put("params", paramArrays);

        return App.vertx.eventBus().<JsonArray>request(
                        Fields.EventBus.EXECUTE_SQL_BATCH_ADDRESS,
                        request
                )

                .map(message ->
                {
                    ConsoleLogger.info("✅ Successfully received batch response for: \n🚀 " + sql);
                    return message.body();
                })

                .onFailure(err ->
                {
                    String paramsStr = params.stream()
                            .map(Tuple::deepToString)
                            .collect(Collectors.joining(", ", "[", "]"));

                    ConsoleLogger.warn("❌ Failed to execute batch " + sql + " with params " + paramsStr + ", error => " + err.getMessage());
                });
    }
}
package org.nms.database.helpers;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Tuple;
import org.nms.App;
import org.nms.Logger;
import org.nms.constants.Fields;

import java.util.List;
import java.util.stream.Collectors;

public class DbEventBus
{
    public static Future<JsonArray> sendQueryExecutionRequest(String query, JsonArray params)
    {
        JsonObject request = new JsonObject()
                .put("query", query)
                .put("params", params);

        try {
            return App.vertx.eventBus().<JsonArray>request(
                            Fields.EventBus.EXECUTE_SQL_WITH_PARAMS_ADDRESS,
                            request
                    )

                    .map(Message::body)

                    .onFailure(err ->
                            Logger.warn("❌ Failed to execute...\n" + query + "\nwith params => " + params.encode() + "\nError => " + err.getMessage()));
        }
        catch (ReplyException replyException)
        {
            Logger.warn("⚠ Query is taking more time then expected to execute");
            return Future.failedFuture("⚠ Query is taking more time then expected to execute");
        }
        catch (Exception e)
        {
            return Future.failedFuture(e);
        }
    }

    public static Future<JsonArray> sendQueryExecutionRequest(String query)
    {
        try {
            return App.vertx.eventBus().<JsonArray>request(
                            Fields.EventBus.EXECUTE_SQL_ADDRESS,
                            query
                    )

                    .map(Message::body)

                    .onFailure(err -> Logger.warn("❌ Failed to execute...\n" + query + "\nError => " + err.getMessage()));
        }
        catch (ReplyException replyException)
        {
            Logger.warn("⚠ Query is taking more time then expected to execute");
            return Future.failedFuture("⚠ Query is taking more time then expected to execute");
        }
        catch (Exception e)
        {
            return Future.failedFuture(e);
        }
    }

    public static Future<JsonArray> sendQueryExecutionRequest(String query, List<Tuple> params)
    {
        // Convert List<Tuple> to JsonArray of JsonArrays for transmission over event bus
        JsonArray dbParams = new JsonArray();

        for (Tuple tuple : params) {
            JsonArray paramArray = new JsonArray();

            // Convert each Tuple to JsonArray
            int size = tuple.size();

            for (int i = 0; i < size; i++) {
                paramArray.add(tuple.getValue(i));
            }

            dbParams.add(paramArray);
        }

        JsonObject request = new JsonObject()
                .put("query", query)
                .put("params", dbParams);

        try
        {
            return App.vertx.eventBus().<JsonArray>request(
                            Fields.EventBus.EXECUTE_SQL_BATCH_ADDRESS,
                            request
                    )

                    .map(Message::body)

                    .onFailure(err ->
                            Logger.warn("❌ Failed to execute...\n" + query + "\nWith params " + params.stream().map(Tuple::deepToString).collect(Collectors.joining(", ", "[", "]")) + "\nError => " + err.getMessage()));
        }
        catch (ReplyException replyException)
        {
            Logger.warn("⚠ Query is taking more time then expected to execute");
            return Future.failedFuture("⚠ Query is taking more time then expected to execute");
        }
        catch (Exception e)
        {
            return Future.failedFuture(e);
        }
    }
}
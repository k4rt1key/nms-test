package org.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.nms.Logger;
import org.nms.constants.Fields;
import org.nms.constants.Queries;
import org.nms.database.helpers.DbEventBus;

import java.util.ArrayList;
import java.util.List;

public class Database extends AbstractVerticle
{
    private io.vertx.sqlclient.SqlClient sqlClient;

    @Override
    public void start(Promise<Void> startPromise)
    {
        try {
            sqlClient = SqlClient.client;

            if (sqlClient == null)
            {
                startPromise.fail("❌ SqlClient is null");
            }

            vertx.eventBus().localConsumer(Fields.EventBus.EXECUTE_SQL_ADDRESS, this::handleExecuteSql);

            vertx.eventBus().localConsumer(Fields.EventBus.EXECUTE_SQL_WITH_PARAMS_ADDRESS, this::handleExecuteSqlWithParams);

            vertx.eventBus().localConsumer(Fields.EventBus.EXECUTE_SQL_BATCH_ADDRESS, this::handleExecuteSqlBatch);

            Future.join(List.of(
                    DbEventBus.sendQueryExecutionRequest(Queries.User.CREATE_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Credential.CREATE_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Discovery.CREATE_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Discovery.CREATE_DISCOVERY_CREDENTIAL_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Discovery.CREATE_DISCOVERY_RESULT_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Monitor.CREATE_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.Monitor.CREATE_METRIC_GROUP_SCHEMA),
                    DbEventBus.sendQueryExecutionRequest(Queries.PollingResult.CREATE_SCHEMA)
            )).onComplete(allSchemasCreated ->
            {
                if(allSchemasCreated.succeeded())
                {
                    Logger.info("✅ Successfully deployed Database Verticle");
                    startPromise.complete();
                }
                else
                {
                    Logger.warn("⚠ Something went wrong creating db schema");
                }
            });
        }
        catch (Exception e)
        {
            startPromise.fail("❌ Failed to deploy database, error => " + e.getMessage());
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        sqlClient
                .close()
                .onComplete(clientClose ->
                {
                    if(clientClose.succeeded())
                    {
                        Logger.info("\uD83D\uDED1 Database Verticle Stopped");

                        stopPromise.complete();
                    }
                    else
                    {
                        stopPromise.fail("❌ Failed to close database connection, error => " + clientClose.cause().getMessage());
                    }
                });
    }

    private void handleExecuteSql(Message<String> message) {
        String query = message.body();

        sqlClient.preparedQuery(query)

                .execute()

                .map(this::toJsonArray)

                .onComplete(dbResult ->
                {
                    if(dbResult.succeeded())
                    {
                        message.reply(dbResult.result());
                    }
                    else
                    {
                        message.fail(500, "❌ Failed to execute...\n" + query + "\nerror => " + dbResult.cause().getMessage());
                    }
                });
    }

    private void handleExecuteSqlWithParams(Message<JsonObject> message)
    {
        JsonObject request = message.body();

        String query = request.getString("query");

        JsonArray params = request.getJsonArray("params");

        sqlClient.preparedQuery(query)

                .execute(Tuple.wrap(params.getList().toArray()))

                .map(this::toJsonArray)

                .onComplete(dbResult ->
                {
                    if(dbResult.succeeded())
                    {
                        message.reply(dbResult.result());
                    }
                    else
                    {
                        message.fail(500, "❌ Failed to execute...\n" + query + "\nerror => " + dbResult.cause().getMessage());
                    }
                });
    }

    private void handleExecuteSqlBatch(Message<JsonObject> message)
    {
        var request = message.body();

        var query = request.getString("query");

        var paramsArray = request.getJsonArray("params");

        var tuples = new ArrayList<Tuple>();

        for (int i = 0; i < paramsArray.size(); i++)
        {
            var params = paramsArray.getJsonArray(i);

            tuples.add(Tuple.wrap(params.getList().toArray()));
        }

        sqlClient.preparedQuery(query)

                .executeBatch(tuples)

                .map(this::toJsonArray)

                .onComplete(dbResult ->
                {
                    if(dbResult.succeeded())
                    {
                        message.reply(dbResult.result());
                    }
                    else
                    {
                        message.fail(500, "❌ Failed to execute...\n" + query + "\nerror => " + dbResult.cause().getMessage());
                    }
                });
    }

    private JsonArray toJsonArray(RowSet<Row> rows)
    {
        var results = new JsonArray();

        for (Row row : rows) {
            results.add(row.toJson());
        }

        return results;
    }
}

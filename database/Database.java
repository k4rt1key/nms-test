package org.nms.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import org.nms.ConsoleLogger;
import org.nms.constants.Fields;

import java.util.ArrayList;

public class Database extends AbstractVerticle
{
    // Database configuration
    private static final String DB_URL = "localhost";
    private static final int DB_PORT = 5000;
    private static final String DB_NAME = "nms";
    private static final String DB_USER = "nms";
    private static final String DB_PASSWORD = "nms";

    private SqlClient client;

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            // Create database connection
            createPostgresClient();

            // Register event bus handlers
            registerEventBusHandlers();

            ConsoleLogger.info("✅ Database verticle started successfully");

            startPromise.complete();
        }
        catch (Exception e)
        {
            ConsoleLogger.error("❌ Failed to start database verticle: " + e.getMessage());

            startPromise.fail(e);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (client != null)
        {
            client.close()
                    .onSuccess(v ->
                    {
                        ConsoleLogger.info("✅ Database connection closed");
                        stopPromise.complete();
                    })
                    .onFailure(err ->
                    {
                        ConsoleLogger.error("❌ Failed to close database connection: " + err.getMessage());
                        stopPromise.fail(err);
                    });
        }
        else
        {
            stopPromise.complete();
        }
    }

    private void createPostgresClient()
    {
        try
        {
            var connectOptions = new PgConnectOptions()
                    .setPort(DB_PORT)
                    .setHost(DB_URL)
                    .setDatabase(DB_NAME)
                    .setUser(DB_USER)
                    .setPassword(DB_PASSWORD);

            var poolOptions = new PoolOptions().setMaxSize(5);

            client = PgBuilder
                    .client()
                    .with(poolOptions)
                    .connectingTo(connectOptions)
                    .using(vertx)
                    .build();

            ConsoleLogger.info("✅ Postgres client created successfully");
        }
        catch (Exception e)
        {
            ConsoleLogger.error("❌ Failed to create Postgres client: " + e.getMessage());
            throw e;
        }
    }

    private void registerEventBusHandlers()
    {
        // Handler for simple SQL execution
        vertx.eventBus().consumer(Fields.EventBus.EXECUTE_SQL_ADDRESS, this::handleExecuteSql);

        // Handler for SQL execution with parameters
        vertx.eventBus().consumer(Fields.EventBus.EXECUTE_SQL_WITH_PARAMS_ADDRESS, this::handleExecuteSqlWithParams);

        // Handler for batch SQL execution
        vertx.eventBus().consumer(Fields.EventBus.EXECUTE_SQL_BATCH_ADDRESS, this::handleExecuteSqlBatch);
    }

    private void handleExecuteSql(Message<String> message)
    {
        String sql = message.body();

        if (client != null)
        {
            client.preparedQuery(sql)

                    .execute()

                    .map(this::toJsonArray)

                    .onSuccess(result ->
                    {
                        ConsoleLogger.info("✅ Successfully executed: \n🚀 " + sql);
                        message.reply(result);
                    })

                    .onFailure(err ->
                    {
                        ConsoleLogger.warn("❌ Failed to execute " + sql + ", error => " + err.getMessage());
                        message.fail(500, err.getMessage());
                    });
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");
            message.fail(500, "❌ Postgres Client Is Null");
        }
    }

    private void handleExecuteSqlWithParams(Message<JsonObject> message)
    {
        JsonObject request = message.body();
        String sql = request.getString("sql");
        JsonArray params = request.getJsonArray("params");

        if (client != null)
        {
            client.preparedQuery(sql)

                    .execute(Tuple.wrap(params.getList().toArray()))

                    .map(this::toJsonArray)

                    .onSuccess(result ->
                    {
                        ConsoleLogger.info("✅ Successfully executed: \n🚀 " + sql + "\n🚀 With Params " + params.encode());
                        message.reply(result);
                    })

                    .onFailure(err ->
                    {
                        ConsoleLogger.warn("❌ Failed to execute " + sql + ", error => " + err.getMessage());
                        message.fail(500, err.getMessage());
                    });
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");
            message.fail(500, "❌ Postgres Client Is Null");
        }
    }

    private void handleExecuteSqlBatch(Message<JsonObject> message)
    {
        var request = message.body();
        var sql = request.getString("sql");
        var paramsArray = request.getJsonArray("params");

        var tuples = new ArrayList<Tuple>();

        for (int i = 0; i < paramsArray.size(); i++)
        {
            var params = paramsArray.getJsonArray(i);
            tuples.add(Tuple.wrap(params.getList().toArray()));
        }

        if (client != null)
        {
            client.preparedQuery(sql)

                    .executeBatch(tuples)

                    .map(this::toJsonArray)

                    .onSuccess(result ->
                    {
                        ConsoleLogger.info("✅ Successfully executed batch: \n🚀 " + sql);
                        message.reply(result);
                    })

                    .onFailure(err ->
                    {
                        ConsoleLogger.warn("❌ Failed to execute batch " + sql + ", error => " + err.getMessage());
                        message.fail(500, err.getMessage());
                    });
        }
        else
        {
            ConsoleLogger.error("❌ Postgres Client Is Null");
            message.fail(500, "❌ Postgres Client Is Null");
        }
    }

    private JsonArray toJsonArray(RowSet<Row> rows)
    {
        var jsonArray = new JsonArray();

        for (Row row : rows)
        {
            jsonArray.add(row.toJson());
        }

        return jsonArray;
    }
}
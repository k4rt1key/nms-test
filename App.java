package org.nms;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.nms.constants.Queries;
import org.nms.database.Database;
import org.nms.database.DbEngine;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.nms.api.Server;
import org.nms.discovery.Discovery;
import org.nms.plugin.Plugin;
import org.nms.scheduler.Scheduler;

public class App
{
    public static Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(300).setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS));

    public static void main( String[] args )
    {

        try
        {
            // ===== Start DB Verticle =====
            vertx.deployVerticle(new Database())
                    // ===== Create Schemas if not exist ======
                    .compose(v ->    Future.join(List.of(
                            DbEngine.execute(Queries.User.CREATE_SCHEMA),
                            DbEngine.execute(Queries.Credential.CREATE_SCHEMA),
                            DbEngine.execute(Queries.Discovery.CREATE_SCHEMA),
                            DbEngine.execute(Queries.Discovery.CREATE_DISCOVERY_CREDENTIAL_SCHEMA),
                            DbEngine.execute(Queries.Discovery.CREATE_DISCOVERY_RESULT_SCHEMA),
                            DbEngine.execute(Queries.Monitor.CREATE_SCHEMA),
                            DbEngine.execute(Queries.Monitor.CREATE_METRIC_GROUP_SCHEMA),
                            DbEngine.execute(Queries.PollingResult.CREATE_SCHEMA)
                    )))
                    // ===== Start Scheduler =====
                    .compose(v -> vertx.deployVerticle(new Scheduler()))
                    // ===== Deploy Plugin Verticle =====
                    .compose(v -> vertx.deployVerticle(new Plugin()))
                    // ===== Deploy Discovery Verticle ======
                    .compose(v -> vertx.deployVerticle(new Discovery()))
                    // ===== Start Http Server =====
                    .compose(v -> vertx.deployVerticle(new Server()))
                    // ===== Success =====
                    .onSuccess(v -> ConsoleLogger.info("✅ Successfully Started NMS Application"))
                    // ===== Failure =====
                    .onFailure(err -> ConsoleLogger.error("❌ Failed to start NMS Application " + err.getMessage()));
        }
        catch (Exception e)
        {
            ConsoleLogger.error("❌ Error Starting Application");
        }
    }
}

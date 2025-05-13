package org.nms;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.nms.constants.Config;
import org.nms.database.Database;

import java.util.concurrent.TimeUnit;

import org.nms.api.Server;
import org.nms.discovery.Discovery;
import org.nms.plugin.Plugin;
import org.nms.scheduler.Scheduler;

public class App
{
    public static Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(Config.MAX_WORKER_EXECUTE_TIME).setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS));

    public static void main(String[] args)
    {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vertx.close();
        }));

        try
        {
            vertx.deployVerticle(new Database())
                    .compose(v -> vertx.deployVerticle(new Scheduler()))
                    .compose(v -> vertx.deployVerticle(new Plugin()))
                    .compose(v -> vertx.deployVerticle(new Discovery()))
                    .compose(v -> vertx.deployVerticle(new Server()))
            .onComplete(ar ->
            {
                if (ar.succeeded())
                {
                    Logger.info("✅ Successfully Started NMS Application");
                }
                else
                {
                    Logger.error("❌ Failed to start NMS Application, cause => " + ar.cause().getMessage());
                }
            });
        }
        catch (Exception e)
        {
            Logger.error("❌ Failed to start NMS Application, cause => " + e.getMessage());
        }
    }
}
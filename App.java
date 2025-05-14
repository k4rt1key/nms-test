package org.nms;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.concurrent.TimeUnit;

import org.nms.api.Server;
import org.nms.constants.Config;
import org.nms.database.Database;
import org.nms.discovery.Discovery;


public class App
{
    public static final Vertx vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(Config.MAX_WORKER_EXECUTE_TIME).setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS));

    public static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args)
    {

        Runtime.getRuntime().addShutdownHook(new Thread(vertx::close));

        try
        {
            vertx.deployVerticle(new Database())

                    .compose(v -> vertx.deployVerticle(new Scheduler()))

                    .compose(v -> vertx.deployVerticle(new Plugin()))

                    .compose(v -> vertx.deployVerticle(new Discovery()))
                    
                    .compose(v -> vertx.deployVerticle(new Server()))

            .onComplete(asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    logger.info("✅ Successfully Started NMS Application");
                }
                else
                {
                    logger.error("❌ Failed to start NMS Application, cause => " + asyncResult.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("❌ Failed to start NMS Application, cause => " + exception.getMessage());
        }
    }
}
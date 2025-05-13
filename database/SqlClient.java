package org.nms.database;

import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.nms.App;
import org.nms.constants.Config;

public class SqlClient
{
    private static final PgConnectOptions connectOptions = new PgConnectOptions()
                                                                .setPort(Config.DB_PORT)
                                                                .setHost(Config.DB_URL)
                                                                .setDatabase(Config.DB_NAME)
                                                                .setUser(Config.DB_USER)
                                                                .setPassword(Config.DB_PASSWORD);

    private static final PoolOptions poolOptions = new PoolOptions()
                                                        .setMaxSize(5);

    public static final io.vertx.sqlclient.SqlClient client = PgBuilder
                                                                .client()
                                                                .with(poolOptions)
                                                                .connectingTo(connectOptions)
                                                                .using(App.vertx)
                                                                .build();
}

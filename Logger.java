package org.nms;

import io.vertx.core.impl.logging.LoggerFactory;


public class Logger
{
    public static final io.vertx.core.impl.logging.Logger logger = LoggerFactory.getLogger(Logger.class);

    public static void info(String message)
    {
        logger.info(message);
    }

    public static void error(String message)
    {
        logger.error(message);
    }

    public static void debug(String message)
    {
        logger.debug(message);
    }

    public static void warn(String message)
    {
        logger.warn(message);
    }
}

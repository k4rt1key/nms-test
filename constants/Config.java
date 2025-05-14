package org.nms.constants;

public class Config
{
    public static final Boolean PRODUCTION = false;

    public static final String KEYSTORE_PATH = "keystore.jks";

    public static final String JWT_SECRET = "secret";

    public static final String PLUGIN_PATH = "src/main/plugin/nms-plugin";

    public static final Integer HTTP_PORT = 8080;

    public static final Integer MAX_WORKER_EXECUTE_TIME = 1500;

    public static final String INDIA_ZONE_NAME = "Asia/Kolkata";

    public static final long MAX_IP_COUNT = 1024;

    // ===== DB =====
    public static final Integer DB_PORT = 5000;

    public static final String DB_URL = "localhost";

    public static final String DB_NAME = "nms";

    public static final String DB_USER = "nms";

    public static final String DB_PASSWORD = "nms";

    // ====== Time ( in seconds ) =====
    public static final int SCHEDULER_CHECKING_INTERVAL = 30;

    public static final int PORT_TIMEOUT = 1;

    public static final int BASE_TIME = 5;

    public static final int DISCOVERY_TIMEOUT_PER_IP = 1;

    public static final int POLLING_TIMEOUT_PER_METRIC_GROUP = 10;
}

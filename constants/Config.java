package org.nms.constants;

public class Config
{
    public static final Boolean PRODUCTION = false;

    public static final String KEYSTORE_PATH = "keystore.jks";

    public static final String JWT_SECRET = "secret";

    public static final String PLUGIN_PATH = "src/main/plugin/nms-plugin";

    public static final int INITIAL_OVERHEAD = 5;

    public static final int DISCOVERY_TIMEOUT_PER_IP = 1;

    public static final int POLLING_TIMEOUT_PER_METRIC_GROUP = 10;
}

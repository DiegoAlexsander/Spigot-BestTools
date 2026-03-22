package de.jeff_media.BestTools.network.config;

/**
 * Config path constants for multi-server settings.
 */
public final class NetworkConfig {

    private NetworkConfig() {}

    // Storage mode
    public static final String STORAGE_MODE = "storage-mode";

    // MySQL
    public static final String MYSQL_HOST           = "mysql.host";
    public static final String MYSQL_PORT           = "mysql.port";
    public static final String MYSQL_DATABASE       = "mysql.database";
    public static final String MYSQL_USERNAME       = "mysql.username";
    public static final String MYSQL_PASSWORD       = "mysql.password";
    public static final String MYSQL_TABLE_PREFIX   = "mysql.table-prefix";
    public static final String MYSQL_POOL_MAX_SIZE     = "mysql.connection-pool.maximum-pool-size";
    public static final String MYSQL_POOL_MIN_IDLE     = "mysql.connection-pool.minimum-idle";
    public static final String MYSQL_POOL_MAX_LIFETIME = "mysql.connection-pool.max-lifetime";
    public static final String MYSQL_POOL_CONN_TIMEOUT = "mysql.connection-pool.connection-timeout";

    // Redis
    public static final String REDIS_HOST       = "redis.host";
    public static final String REDIS_PORT       = "redis.port";
    public static final String REDIS_USERNAME   = "redis.username";
    public static final String REDIS_PASSWORD   = "redis.password";
    public static final String REDIS_DATABASE   = "redis.database";
    public static final String REDIS_USE_SSL    = "redis.use-ssl";
    public static final String REDIS_TIMEOUT    = "redis.timeout";
    public static final String REDIS_CLUSTER_ID = "redis.cluster-id";
    public static final String REDIS_SENTINEL_MASTER   = "redis.sentinel.master";
    public static final String REDIS_SENTINEL_NODES    = "redis.sentinel.nodes";
    public static final String REDIS_SENTINEL_PASSWORD = "redis.sentinel.password";
    public static final String REDIS_SENTINEL_USERNAME = "redis.sentinel.username";
}

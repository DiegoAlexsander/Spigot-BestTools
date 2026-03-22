package de.jeff_media.BestTools.network.redis;

import de.jeff_media.BestTools.Main;
import de.jeff_media.BestTools.network.config.NetworkConfig;
import redis.clients.jedis.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Manages the Jedis connection pool and Redis pub/sub subscription.
 */
public class RedisManager {

    private final Main plugin;
    private JedisPool pool;
    private JedisSentinelPool sentinelPool;
    private Thread subscriberThread;
    private volatile boolean running = true;
    private String channel;
    private BiConsumer<String, String> messageHandler;

    public RedisManager(Main plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────── lifecycle ───────────────────────────

    public void initialize() throws Exception {
        String host     = plugin.getConfig().getString(NetworkConfig.REDIS_HOST, "localhost");
        int    port     = plugin.getConfig().getInt   (NetworkConfig.REDIS_PORT, 6379);
        String username = plugin.getConfig().getString(NetworkConfig.REDIS_USERNAME, "");
        String password = plugin.getConfig().getString(NetworkConfig.REDIS_PASSWORD, "");
        int    database = plugin.getConfig().getInt   (NetworkConfig.REDIS_DATABASE, 0);
        boolean useSsl  = plugin.getConfig().getBoolean(NetworkConfig.REDIS_USE_SSL, false);
        int    timeout  = plugin.getConfig().getInt   (NetworkConfig.REDIS_TIMEOUT, 2000);
        String clusterId = plugin.getConfig().getString(NetworkConfig.REDIS_CLUSTER_ID, "main");

        channel = "besttools:" + clusterId;

        // Build client config
        DefaultJedisClientConfig.Builder clientBuilder = DefaultJedisClientConfig.builder()
                .database(database)
                .ssl(useSsl)
                .timeoutMillis(timeout);

        if (password != null && !password.isEmpty()) {
            clientBuilder.password(password);
        }
        if (username != null && !username.isEmpty()) {
            clientBuilder.user(username);
        }

        JedisClientConfig clientConfig = clientBuilder.build();

        // Sentinel or standalone
        String sentinelMaster = plugin.getConfig().getString(NetworkConfig.REDIS_SENTINEL_MASTER, "");
        if (sentinelMaster != null && !sentinelMaster.isEmpty()) {
            List<String> sentinelNodes = plugin.getConfig().getStringList(NetworkConfig.REDIS_SENTINEL_NODES);
            String sentinelPassword = plugin.getConfig().getString(NetworkConfig.REDIS_SENTINEL_PASSWORD, "");
            String sentinelUsername = plugin.getConfig().getString(NetworkConfig.REDIS_SENTINEL_USERNAME, "");

            DefaultJedisClientConfig.Builder sentinelBuilder = DefaultJedisClientConfig.builder()
                    .timeoutMillis(timeout);
            if (sentinelPassword != null && !sentinelPassword.isEmpty()) {
                sentinelBuilder.password(sentinelPassword);
            }
            if (sentinelUsername != null && !sentinelUsername.isEmpty()) {
                sentinelBuilder.user(sentinelUsername);
            }
            JedisClientConfig sentinelClientConfig = sentinelBuilder.build();

            // Jedis 5.x: JedisSentinelPool takes Set<HostAndPort>
            Set<HostAndPort> nodesSet = new HashSet<>();
            for (String node : sentinelNodes) {
                nodesSet.add(HostAndPort.from(node));
            }

            JedisPoolConfig poolConfig = new JedisPoolConfig();
            sentinelPool = new JedisSentinelPool(sentinelMaster, nodesSet,
                    poolConfig, clientConfig, sentinelClientConfig);
        } else {
            pool = new JedisPool(new JedisPoolConfig(),
                    new HostAndPort(host, port), clientConfig);
        }

        // Test connection
        try (Jedis jedis = getResource()) {
            String pong = jedis.ping();
            plugin.getLogger().info("[Redis] Connected to " + host + ":" + port
                    + " (db=" + database + ", ssl=" + useSsl + ")");
            plugin.getLogger().info("[Redis] PING → " + pong);
        }
    }

    public void shutdown() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            subscriberThread = null;
        }
        if (pool != null) {
            pool.close();
            pool = null;
        }
        if (sentinelPool != null) {
            sentinelPool.close();
            sentinelPool = null;
        }
        plugin.getLogger().info("[Redis] Connection closed.");
    }

    // ─────────────────────────── pub/sub ─────────────────────────────

    public void publish(String message) {
        try (Jedis jedis = getResource()) {
            jedis.publish(channel, message);
            plugin.debug("[Redis] Published: " + message);
        } catch (Exception e) {
            plugin.getLogger().warning("[Redis] Failed to publish: " + e.getMessage());
        }
    }

    public void subscribe(BiConsumer<String, String> handler) {
        this.messageHandler = handler;
        subscriberThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = getResource()) {
                    plugin.debug("[Redis] Subscribing to channel: " + channel);
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String ch, String message) {
                            plugin.debug("[Redis] Received on " + ch + ": " + message);
                            if (messageHandler != null) {
                                messageHandler.accept(ch, message);
                            }
                        }

                        @Override
                        public void onUnsubscribe(String ch, int subscribedChannels) {
                            // Will exit the blocking subscribe call
                        }
                    }, channel);
                } catch (Exception e) {
                    if (!running) break;
                    plugin.getLogger().warning("[Redis] Subscriber error: " + e.getMessage()
                            + " — reconnecting in 8s...");
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "BestTools-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    // ─────────────────────────── internal ────────────────────────────

    private Jedis getResource() {
        if (sentinelPool != null) return sentinelPool.getResource();
        if (pool != null) return pool.getResource();
        throw new IllegalStateException("Redis not initialized");
    }
}

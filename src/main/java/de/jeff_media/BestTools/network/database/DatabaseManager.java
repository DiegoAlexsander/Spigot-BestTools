package de.jeff_media.BestTools.network.database;

import com.mysql.cj.jdbc.Driver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.jeff_media.BestTools.Main;
import de.jeff_media.BestTools.network.config.NetworkConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Manages the HikariCP connection pool to MySQL/MariaDB.
 */
public class DatabaseManager {

    @SuppressWarnings("unused")
    private static final Class<Driver> MYSQL_DRIVER_REF = Driver.class;

    private final Main plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;

    public DatabaseManager(Main plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────── lifecycle ───────────────────────────

    public void initialize() throws Exception {
        tablePrefix = plugin.getConfig().getString(NetworkConfig.MYSQL_TABLE_PREFIX, "besttools_");

        String host     = plugin.getConfig().getString(NetworkConfig.MYSQL_HOST,     "localhost");
        int    port     = plugin.getConfig().getInt   (NetworkConfig.MYSQL_PORT,     3306);
        String database = plugin.getConfig().getString(NetworkConfig.MYSQL_DATABASE, "minecraft");
        String username = plugin.getConfig().getString(NetworkConfig.MYSQL_USERNAME, "root");
        String password = plugin.getConfig().getString(NetworkConfig.MYSQL_PASSWORD, "");

        int  maxPoolSize = plugin.getConfig().getInt (NetworkConfig.MYSQL_POOL_MAX_SIZE,     10);
        int  minIdle     = plugin.getConfig().getInt (NetworkConfig.MYSQL_POOL_MIN_IDLE,     2);
        long maxLifetime = plugin.getConfig().getLong(NetworkConfig.MYSQL_POOL_MAX_LIFETIME, 1_800_000L);
        long connTimeout = plugin.getConfig().getLong(NetworkConfig.MYSQL_POOL_CONN_TIMEOUT, 5_000L);

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName(Driver.class.getName()); // shade-safe
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&characterEncoding=utf8");
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(maxPoolSize);
        cfg.setMinimumIdle(minIdle);
        cfg.setMaxLifetime(maxLifetime);
        cfg.setConnectionTimeout(connTimeout);
        cfg.setPoolName("BestTools-MySQL");

        dataSource = new HikariDataSource(cfg);

        createTables();

        plugin.getLogger().info("[MySQL] Connected to " + host + ":" + port + "/" + database);
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[MySQL] Connection pool closed.");
        }
    }

    // ─────────────────────────── access ──────────────────────────────

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }

    public PreparedStatement prepare(Connection conn, String sqlTemplate, Object... params)
            throws SQLException {
        String sql = String.format(sqlTemplate, tablePrefix);
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    // ─────────────────────────── schema ──────────────────────────────

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    String.format(DatabaseSchema.CREATE_PLAYERS_TABLE, tablePrefix))) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    String.format(DatabaseSchema.CREATE_BLACKLIST_TABLE, tablePrefix))) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    String.format(DatabaseSchema.CREATE_METADATA_TABLE, tablePrefix))) {
                ps.executeUpdate();
            }
            // Schema migrations for existing tables
            migrateColumn(conn, "move_to_favorite", "TINYINT(1) NOT NULL DEFAULT 1");
            plugin.debug("[MySQL] Tables verified/created with prefix '" + tablePrefix + "'");
        }
    }

    private void migrateColumn(Connection conn, String column, String definition) {
        try (PreparedStatement ps = conn.prepareStatement(
                "ALTER TABLE " + tablePrefix + "players ADD COLUMN " + column + " " + definition)) {
            ps.executeUpdate();
            plugin.getLogger().info("[MySQL] Added column '" + column + "' to players table.");
        } catch (SQLException e) {
            // Column already exists — ignore
        }
    }
}

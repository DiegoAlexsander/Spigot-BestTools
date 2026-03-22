package de.jeff_media.BestTools.network.database;

/**
 * SQL templates for the BestTools MySQL storage.
 * Each template uses {@code %s} for the configurable table prefix.
 */
public final class DatabaseSchema {

    private DatabaseSchema() {}

    // ── table creation ─────────────────────────────────────────────────

    public static final String CREATE_PLAYERS_TABLE =
            "CREATE TABLE IF NOT EXISTS %splayers ("
            + "uuid CHAR(36) NOT NULL PRIMARY KEY,"
            + "username VARCHAR(16) NOT NULL,"
            + "besttools_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
            + "refill_enabled BOOLEAN NOT NULL DEFAULT FALSE,"
            + "hotbar_only BOOLEAN NOT NULL DEFAULT TRUE,"
            + "has_seen_bt_msg BOOLEAN NOT NULL DEFAULT FALSE,"
            + "has_seen_rf_msg BOOLEAN NOT NULL DEFAULT FALSE,"
            + "favorite_slot INT NOT NULL DEFAULT -1,"
            + "move_to_favorite TINYINT(1) NOT NULL DEFAULT 1,"
            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
            + "INDEX idx_updated_at (updated_at)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    public static final String CREATE_BLACKLIST_TABLE =
            "CREATE TABLE IF NOT EXISTS %sblacklist ("
            + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
            + "uuid CHAR(36) NOT NULL,"
            + "material VARCHAR(128) NOT NULL,"
            + "UNIQUE KEY uk_uuid_material (uuid, material)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    public static final String CREATE_METADATA_TABLE =
            "CREATE TABLE IF NOT EXISTS %smetadata ("
            + "meta_key VARCHAR(64) NOT NULL PRIMARY KEY,"
            + "meta_value VARCHAR(255) NOT NULL"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    // ── player CRUD ────────────────────────────────────────────────────

    public static final String SELECT_PLAYER =
            "SELECT * FROM %splayers WHERE uuid = ?";

    public static final String UPSERT_PLAYER =
            "INSERT INTO %splayers (uuid, username, besttools_enabled, refill_enabled, "
            + "hotbar_only, has_seen_bt_msg, has_seen_rf_msg, favorite_slot, move_to_favorite) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "username=VALUES(username), besttools_enabled=VALUES(besttools_enabled), "
            + "refill_enabled=VALUES(refill_enabled), hotbar_only=VALUES(hotbar_only), "
            + "has_seen_bt_msg=VALUES(has_seen_bt_msg), has_seen_rf_msg=VALUES(has_seen_rf_msg), "
            + "favorite_slot=VALUES(favorite_slot), move_to_favorite=VALUES(move_to_favorite)";

    // ── admin reset ────────────────────────────────────────────────────

    /** Resets all player rows to config defaults. favorite_slot=-1 so each server uses its own config. */
    public static final String RESET_ALL_PLAYERS =
            "UPDATE %splayers SET "
            + "besttools_enabled = ?, refill_enabled = ?, hotbar_only = ?, "
            + "favorite_slot = -1, move_to_favorite = 1, has_seen_bt_msg = 0, has_seen_rf_msg = 0";

    /** Clears the entire blacklist table (used during resetplayersettings). */
    public static final String DELETE_ALL_BLACKLISTS =
            "DELETE FROM %sblacklist";

    // ── blacklist CRUD ─────────────────────────────────────────────────

    public static final String SELECT_BLACKLIST =
            "SELECT material FROM %sblacklist WHERE uuid = ?";

    public static final String INSERT_BLACKLIST =
            "INSERT IGNORE INTO %sblacklist (uuid, material) VALUES (?, ?)";

    public static final String DELETE_BLACKLIST_ITEM =
            "DELETE FROM %sblacklist WHERE uuid = ? AND material = ?";

    public static final String DELETE_BLACKLIST_ALL =
            "DELETE FROM %sblacklist WHERE uuid = ?";

    // ── metadata ───────────────────────────────────────────────────────

    public static final String SET_METADATA =
            "INSERT INTO %smetadata (meta_key, meta_value) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";

    public static final String GET_METADATA =
            "SELECT meta_value FROM %smetadata WHERE meta_key = ?";
}

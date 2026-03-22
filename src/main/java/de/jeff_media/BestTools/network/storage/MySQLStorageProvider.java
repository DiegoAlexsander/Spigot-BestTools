package de.jeff_media.BestTools.network.storage;

import de.jeff_media.BestTools.Blacklist;
import de.jeff_media.BestTools.Main;
import de.jeff_media.BestTools.PlayerSetting;
import de.jeff_media.BestTools.network.database.DatabaseManager;
import de.jeff_media.BestTools.network.database.DatabaseSchema;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MySQL-backed storage for player settings and blacklist.
 */
public class MySQLStorageProvider implements StorageProvider {

    private final Main plugin;
    private final DatabaseManager db;

    public MySQLStorageProvider(Main plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public void initialize() throws Exception {
        db.initialize();
    }

    @Override
    public void shutdown() {
        db.shutdown();
    }

    // ─────────────────────────── load ────────────────────────────────

    @Override
    public PlayerSetting loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        try (Connection conn = db.getConnection()) {
            // Load player row
            try (PreparedStatement ps = db.prepare(conn, DatabaseSchema.SELECT_PLAYER, uuid.toString())) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    boolean btEnabled  = rs.getBoolean("besttools_enabled");
                    boolean rfEnabled  = rs.getBoolean("refill_enabled");
                    boolean hotbar     = rs.getBoolean("hotbar_only");
                    boolean seenBt     = rs.getBoolean("has_seen_bt_msg");
                    boolean seenRf     = rs.getBoolean("has_seen_rf_msg");
                    int     favSlot    = rs.getInt("favorite_slot");
                    boolean moveToFav  = rs.getBoolean("move_to_favorite");
                    if (favSlot < 0) favSlot = plugin.getConfig().getInt("favorite-slot");

                    // Load blacklist
                    List<String> blStrings = new ArrayList<>();
                    try (PreparedStatement blPs = db.prepare(conn, DatabaseSchema.SELECT_BLACKLIST, uuid.toString())) {
                        ResultSet blRs = blPs.executeQuery();
                        while (blRs.next()) {
                            blStrings.add(blRs.getString("material"));
                        }
                    }

                    PlayerSetting setting = PlayerSetting.fromDatabase(player, btEnabled, rfEnabled,
                            hotbar, seenBt, seenRf, new Blacklist(blStrings),
                            favSlot, moveToFav,
                            plugin.getConfig().getBoolean("use-sword-on-hostile-mobs"));
                    plugin.debug("[MySQL] Loaded settings for " + player.getName());
                    return setting;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[MySQL] Failed to load settings for " + player.getName() + ": " + e.getMessage());
        }

        // Not in DB yet — create with defaults and insert
        PlayerSetting setting = PlayerSetting.fromDatabase(player,
                plugin.getConfig().getBoolean("besttools-enabled-by-default"),
                plugin.getConfig().getBoolean("refill-enabled-by-default"),
                plugin.getConfig().getBoolean("hotbar-only"),
                false, false, new Blacklist(),
                plugin.getConfig().getInt("favorite-slot"),
                true,
                plugin.getConfig().getBoolean("use-sword-on-hostile-mobs"));
        savePlayerAsync(uuid, setting, player.getName());
        plugin.debug("[MySQL] Created default settings for " + player.getName());
        return setting;
    }

    // ─────────────────────────── save ────────────────────────────────

    @Override
    public void savePlayer(UUID uuid, PlayerSetting setting) {
        Player p = plugin.getServer().getPlayer(uuid);
        String username = p != null ? p.getName() : uuid.toString().substring(0, 16);
        savePlayerAsync(uuid, setting, username);
    }

    private void savePlayerAsync(UUID uuid, PlayerSetting setting, String username) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.isClosed()) {
                plugin.debug("[MySQL] Skipping save for " + username + " — pool closed (reload in progress)");
                return;
            }
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = db.prepare(conn, DatabaseSchema.UPSERT_PLAYER,
                         uuid.toString(), username,
                         setting.isBestToolsEnabled()  ? 1 : 0,
                         setting.isRefillEnabled()     ? 1 : 0,
                         setting.isHotbarOnly()        ? 1 : 0,
                         setting.isHasSeenBestToolsMessage() ? 1 : 0,
                         setting.isHasSeenRefillMessage()    ? 1 : 0,
                         setting.getFavoriteSlot(),
                         setting.isMoveToFavorite() ? 1 : 0)) {
                ps.executeUpdate();
                plugin.debug("[MySQL] Saved settings for " + username);
            } catch (SQLException e) {
                plugin.getLogger().warning("[MySQL] Failed to save settings for "
                        + username + ": " + e.getMessage());
            }
        });
    }

    // ─────────────────────────── blacklist ───────────────────────────

    @Override
    public void addToBlacklist(UUID uuid, List<Material> materials) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.isClosed()) return;
            try (Connection conn = db.getConnection()) {
                for (Material mat : materials) {
                    try (PreparedStatement ps = db.prepare(conn, DatabaseSchema.INSERT_BLACKLIST,
                            uuid.toString(), mat.name())) {
                        ps.executeUpdate();
                    }
                }
                plugin.debug("[MySQL] Added " + materials.size() + " blacklist entries for " + uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("[MySQL] Failed to add blacklist for " + uuid + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void removeFromBlacklist(UUID uuid, List<Material> materials) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.isClosed()) return;
            try (Connection conn = db.getConnection()) {
                for (Material mat : materials) {
                    try (PreparedStatement ps = db.prepare(conn, DatabaseSchema.DELETE_BLACKLIST_ITEM,
                            uuid.toString(), mat.name())) {
                        ps.executeUpdate();
                    }
                }
                plugin.debug("[MySQL] Removed " + materials.size() + " blacklist entries for " + uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("[MySQL] Failed to remove blacklist for " + uuid + ": " + e.getMessage());
            }
        });
    }

    @Override
    public void clearBlacklist(UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (db.isClosed()) return;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = db.prepare(conn, DatabaseSchema.DELETE_BLACKLIST_ALL, uuid.toString())) {
                ps.executeUpdate();
                plugin.debug("[MySQL] Cleared blacklist for " + uuid);
            } catch (SQLException e) {
                plugin.getLogger().warning("[MySQL] Failed to clear blacklist for " + uuid + ": " + e.getMessage());
            }
        });
    }

    // ─────────────────────────── fingerprint / reset ─────────────────

    @Override
    public void resetAllPlayers(int newFingerprint) {
        if (db.isClosed()) return;
        boolean defBt     = plugin.getConfig().getBoolean("besttools-enabled-by-default");
        boolean defRf     = plugin.getConfig().getBoolean("refill-enabled-by-default");
        boolean defHotbar = plugin.getConfig().getBoolean("hotbar-only");
        try (Connection conn = db.getConnection();
             PreparedStatement ps = db.prepare(conn, DatabaseSchema.RESET_ALL_PLAYERS,
                     defBt ? 1 : 0, defRf ? 1 : 0, defHotbar ? 1 : 0)) {
            int rows = ps.executeUpdate();
            plugin.getLogger().info("[MySQL] Reset " + rows + " player rows to defaults.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[MySQL] Failed to reset all players: " + e.getMessage());
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = db.prepare(conn, DatabaseSchema.DELETE_ALL_BLACKLISTS)) {
            ps.executeUpdate();
            plugin.getLogger().info("[MySQL] Cleared all player blacklists.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[MySQL] Failed to clear all blacklists: " + e.getMessage());
        }
        saveFingerprint(newFingerprint);
    }

    @Override
    public void saveFingerprint(int fingerprint) {
        if (db.isClosed()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = db.prepare(conn, DatabaseSchema.SET_METADATA,
                     "settings_fingerprint", String.valueOf(fingerprint))) {
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[MySQL] Failed to save fingerprint: " + e.getMessage());
        }
    }

    @Override
    public int getStoredFingerprint() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = db.prepare(conn, DatabaseSchema.GET_METADATA, "settings_fingerprint")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Integer.parseInt(rs.getString("meta_value"));
        } catch (Exception e) {
            // No fingerprint stored yet or parse error — treat as 0
        }
        return 0;
    }
}

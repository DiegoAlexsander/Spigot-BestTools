package de.jeff_media.BestTools.network.sync;

import de.jeff_media.BestTools.Blacklist;
import de.jeff_media.BestTools.Main;
import de.jeff_media.BestTools.PlayerSetting;
import de.jeff_media.BestTools.network.redis.RedisManager;
import de.jeff_media.BestTools.network.redis.RedisMessage;
import de.jeff_media.BestTools.network.storage.StorageProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates synchronisation between MySQL storage and Redis pub/sub.
 */
public class DataSyncer {

    private final Main plugin;
    private final RedisManager redis;
    private final StorageProvider storage;

    public DataSyncer(Main plugin, RedisManager redis, StorageProvider storage) {
        this.plugin = plugin;
        this.redis = redis;
        this.storage = storage;

        // Subscribe to Redis channel
        redis.subscribe((channel, message) -> {
            // Dispatch to main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> handleMessage(message));
        });
    }

    // ── outgoing: setting changed ───────────────────────────────────

    /**
     * Called when a player toggles a boolean setting or changes favoriteSlot.
     * Saves to MySQL (async) and publishes to Redis.
     */
    public void onSettingChanged(UUID uuid, PlayerSetting setting) {
        storage.savePlayer(uuid, setting);
        // Publish all fields as a full update (7 fields: 5 booleans + favoriteSlot + moveToFavorite)
        String msg = RedisMessage.encodeSettingsUpdate(uuid.toString(), "ALL",
                (setting.isBestToolsEnabled()  ? "1" : "0") + ","
              + (setting.isRefillEnabled()     ? "1" : "0") + ","
              + (setting.isHotbarOnly()        ? "1" : "0") + ","
              + (setting.isHasSeenBestToolsMessage() ? "1" : "0") + ","
              + (setting.isHasSeenRefillMessage()    ? "1" : "0") + ","
              + setting.getFavoriteSlot() + ","
              + (setting.isMoveToFavorite() ? "1" : "0"));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> redis.publish(msg));
    }

    // ── outgoing: blacklist changed ─────────────────────────────────

    public void onBlacklistAdd(UUID uuid, List<Material> materials) {
        storage.addToBlacklist(uuid, materials);
        String mats = materials.stream().map(Material::name).collect(Collectors.joining(","));
        String msg = RedisMessage.encodeBlacklistUpdate(uuid.toString(), "ADD", mats);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> redis.publish(msg));
    }

    public void onBlacklistRemove(UUID uuid, List<Material> materials) {
        storage.removeFromBlacklist(uuid, materials);
        String mats = materials.stream().map(Material::name).collect(Collectors.joining(","));
        String msg = RedisMessage.encodeBlacklistUpdate(uuid.toString(), "REMOVE", mats);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> redis.publish(msg));
    }

    public void onBlacklistReset(UUID uuid) {
        storage.clearBlacklist(uuid);
        String msg = RedisMessage.encodeBlacklistUpdate(uuid.toString(), "RESET", "");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> redis.publish(msg));
    }

    // ── outgoing: admin reset all ───────────────────────────────────

    /**
     * Resets all player settings to config defaults and broadcasts RESET_ALL.
     * Called SYNCHRONOUSLY so MySQL completes before load(true) shuts down the pool.
     */
    public void onResetAll(int newFingerprint) {
        storage.resetAllPlayers(newFingerprint);
        redis.publish(RedisMessage.encodeResetAll(newFingerprint));
    }

    // ── incoming: from Redis ────────────────────────────────────────

    private void handleMessage(String raw) {
        RedisMessage type = RedisMessage.parseType(raw);
        if (type == null) return;

        // RESET_ALL has no UUID — handle before uuid extraction
        if (type == RedisMessage.RESET_ALL) {
            int fingerprint = RedisMessage.parseFingerprint(raw);
            if (fingerprint == plugin.getSettingsFingerprint()) {
                plugin.debug("[Redis] Ignoring own RESET_ALL (fingerprint=" + fingerprint + ")");
                return;
            }
            plugin.getLogger().info("[Redis] Received RESET_ALL (fingerprint=" + fingerprint + ") — reloading");
            plugin.incrementFingerprintFromRemote(fingerprint);
            return;
        }

        String payload = RedisMessage.payload(raw);
        String uuid = RedisMessage.parseUUID(payload);

        // Check if this player is online on THIS server
        Player player = plugin.getServer().getPlayer(UUID.fromString(uuid));
        if (player == null || !player.isOnline()) {
            plugin.debug("[Redis] Player " + uuid + " not online — skipped");
            return;
        }

        Map<UUID, PlayerSetting> map = plugin.playerSettings;
        PlayerSetting setting = map.get(player.getUniqueId());
        if (setting == null) {
            plugin.debug("[Redis] No in-memory setting for " + uuid + " — skipped");
            return;
        }

        switch (type) {
            case SETTINGS_UPDATE: {
                String value = RedisMessage.parseValue(payload);
                // Check if it's our own message (values already match) — echo prevention
                String currentState =
                        (setting.isBestToolsEnabled()  ? "1" : "0") + ","
                      + (setting.isRefillEnabled()     ? "1" : "0") + ","
                      + (setting.isHotbarOnly()        ? "1" : "0") + ","
                      + (setting.isHasSeenBestToolsMessage() ? "1" : "0") + ","
                      + (setting.isHasSeenRefillMessage()    ? "1" : "0") + ","
                      + setting.getFavoriteSlot() + ","
                      + (setting.isMoveToFavorite() ? "1" : "0");
                if (currentState.equals(value)) {
                    plugin.debug("[Redis] Ignoring own SETTINGS_UPDATE for " + player.getName());
                    break;
                }
                // Apply remote values
                String[] parts = value.split(",");
                if (parts.length >= 7) {
                    int favSlot;
                    try { favSlot = Integer.parseInt(parts[5]); } catch (NumberFormatException e) { favSlot = -1; }
                    boolean moveToFav = "1".equals(parts[6]);
                    setting.applyRemote(
                            "1".equals(parts[0]),
                            "1".equals(parts[1]),
                            "1".equals(parts[2]),
                            "1".equals(parts[3]),
                            "1".equals(parts[4]),
                            favSlot,
                            moveToFav
                    );
                    plugin.debug("[Redis] Applied SETTINGS_UPDATE for " + player.getName());
                } else if (parts.length >= 6) {
                    // Backwards compat: 6-field messages from older servers
                    int favSlot;
                    try { favSlot = Integer.parseInt(parts[5]); } catch (NumberFormatException e) { favSlot = -1; }
                    setting.applyRemote(
                            "1".equals(parts[0]),
                            "1".equals(parts[1]),
                            "1".equals(parts[2]),
                            "1".equals(parts[3]),
                            "1".equals(parts[4]),
                            favSlot,
                            setting.isMoveToFavorite()
                    );
                    plugin.debug("[Redis] Applied SETTINGS_UPDATE (6-field compat) for " + player.getName());
                }
                break;
            }
            case BLACKLIST_UPDATE: {
                String action = RedisMessage.parseAction(payload);
                String matsStr = RedisMessage.parseMaterials(payload);
                Blacklist bl = setting.getBlacklist();

                switch (action) {
                    case "ADD":
                        for (String mat : matsStr.split(",")) {
                            Material m = Material.getMaterial(mat);
                            if (m != null && !bl.contains(m)) bl.add(m);
                        }
                        break;
                    case "REMOVE":
                        for (String mat : matsStr.split(",")) {
                            Material m = Material.getMaterial(mat);
                            if (m != null) bl.remove(m);
                        }
                        break;
                    case "RESET":
                        bl.mats.clear();
                        break;
                }
                // Invalidate BestToolsCache
                setting.getBtcache().invalidated();
                plugin.debug("[Redis] Applied BLACKLIST_UPDATE (" + action + ") for " + player.getName());
                break;
            }
            default:
                break;
        }
    }
}

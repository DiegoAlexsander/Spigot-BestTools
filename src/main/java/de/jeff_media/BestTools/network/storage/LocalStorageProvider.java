package de.jeff_media.BestTools.network.storage;

import com.jeff_media.morepersistentdatatypes.DataType;
import de.jeff_media.BestTools.Blacklist;
import de.jeff_media.BestTools.Main;
import de.jeff_media.BestTools.PlayerSetting;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * Local storage using the player's Persistent Data Container (PDC).
 * This wraps the original BestTools storage mechanism — behaviour is unchanged.
 */
public class LocalStorageProvider implements StorageProvider {

    private final Main plugin;
    private final NamespacedKey dataKey;

    public LocalStorageProvider(Main plugin) {
        this.plugin = plugin;
        this.dataKey = new NamespacedKey(plugin, "data");
    }

    @Override
    public void initialize() {
        // No-op — PDC is always available
    }

    @Override
    public void shutdown() {
        // No-op
    }

    @Override
    public PlayerSetting loadPlayer(Player player) {
        File legacyFile = plugin.getPlayerDataFile(player.getUniqueId());
        if (legacyFile.exists()) {
            plugin.debug("Loading player setting for " + player.getName() + " from file");
            PlayerSetting setting = new PlayerSetting(player, legacyFile);
            legacyFile.delete();
            return setting;
        }
        plugin.debug("Creating new player setting for " + player.getName());
        return new PlayerSetting(player,
                plugin.getConfig().getBoolean("besttools-enabled-by-default"),
                plugin.getConfig().getBoolean("refill-enabled-by-default"),
                plugin.getConfig().getBoolean("hotbar-only"),
                plugin.getConfig().getInt("favorite-slot"),
                plugin.getConfig().getBoolean("use-sword-on-hostile-mobs"));
    }

    @Override
    public void savePlayer(UUID uuid, PlayerSetting setting) {
        // In LOCAL mode, save() is called by each toggle method via PDC directly.
        // This method exists for the interface; the PDC write happens inside PlayerSetting.save().
    }

    @Override
    public void addToBlacklist(UUID uuid, List<Material> materials) {
        // Already handled in-memory; PDC write happens via PlayerSetting.save()
    }

    @Override
    public void removeFromBlacklist(UUID uuid, List<Material> materials) {
        // Already handled in-memory; PDC write happens via PlayerSetting.save()
    }

    @Override
    public void clearBlacklist(UUID uuid) {
        // Already handled in-memory; PDC write happens via PlayerSetting.save()
    }

    // ─────────────────────────── fingerprint ─────────────────────────

    private File getFingerprintFile() {
        return new File(plugin.getDataFolder(), "settings.fingerprint");
    }

    @Override
    public void resetAllPlayers(int newFingerprint) {
        // In LOCAL mode the fingerprint suffix on PDC keys is the mechanism:
        // old keys are orphaned and new defaults are read on next player load.
        saveFingerprint(newFingerprint);
    }

    @Override
    public void saveFingerprint(int fingerprint) {
        File file = getFingerprintFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("v", fingerprint);
        try {
            yaml.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("[Local] Failed to save fingerprint: " + e.getMessage());
        }
    }

    @Override
    public int getStoredFingerprint() {
        File file = getFingerprintFile();
        if (!file.exists()) return 0;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return yaml.getInt("v", 0);
    }
}

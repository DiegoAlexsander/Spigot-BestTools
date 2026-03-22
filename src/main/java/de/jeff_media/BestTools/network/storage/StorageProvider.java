package de.jeff_media.BestTools.network.storage;

import de.jeff_media.BestTools.Blacklist;
import de.jeff_media.BestTools.PlayerSetting;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Interface for player data persistence.
 */
public interface StorageProvider {

    void initialize() throws Exception;

    void shutdown();

    /**
     * Loads a player's settings. Called from the main thread — implementations
     * must handle async internally when needed.
     */
    PlayerSetting loadPlayer(Player player);

    /**
     * Saves all fields of a player's settings (full upsert).
     */
    void savePlayer(UUID uuid, PlayerSetting setting);

    /**
     * Adds materials to a player's blacklist.
     */
    void addToBlacklist(UUID uuid, List<Material> materials);

    /**
     * Removes materials from a player's blacklist.
     */
    void removeFromBlacklist(UUID uuid, List<Material> materials);

    /**
     * Clears a player's entire blacklist.
     */
    void clearBlacklist(UUID uuid);

    /**
     * Resets all player settings to config defaults and persists the new fingerprint.
     * Called synchronously before load(true) so connections are still alive.
     */
    void resetAllPlayers(int newFingerprint);

    /**
     * Persists the settings fingerprint so other servers (and reloads) can detect resets.
     */
    void saveFingerprint(int fingerprint);

    /**
     * Returns the last persisted fingerprint, or 0 if none.
     */
    int getStoredFingerprint();
}

package de.jeff_media.BestTools;

import com.jeff_media.morepersistentdatatypes.DataType;
import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;

public class PlayerSetting {

        private static final Main main = Main.getInstance();
        private static final NamespacedKey DATA = new NamespacedKey(main, "data");

        @Getter private Blacklist blacklist;

        @Getter
        private boolean bestToolsEnabled;

        @Getter private boolean refillEnabled;

        @Getter private boolean hotbarOnly;

        private int favoriteSlot = 0;

        @Getter private boolean moveToFavorite = true;

        @Getter private boolean swordOnMobs;

        @Getter private boolean hasSeenBestToolsMessage = false;
        @Getter private boolean hasSeenRefillMessage = false;

        // Do we have to save these settings?
        boolean changed = false;

        @Getter private final BestToolsCache btcache = new BestToolsCache();

        private final Player player;

        /**
         * Optional callback invoked whenever a setting is toggled.
         * Set by Main for MYSQL/NETWORK modes so changes are persisted immediately.
         * Null in LOCAL mode (default behaviour unchanged).
         */
        private Runnable onChanged;

        public void setOnChanged(Runnable callback) {
                this.onChanged = callback;
        }

        private void notifyChanged() {
                changed = true;
                if (onChanged != null) onChanged.run();
        }

        public int getFavoriteSlot() {
                if(favoriteSlot >= 0 && favoriteSlot <= 8) return favoriteSlot;
                return player.getInventory().getHeldItemSlot();
        }

        // ── Legacy file constructor ──────────────────────────────────

        public PlayerSetting(Player player, File file) {
                this.player = player;
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                blacklist = new Blacklist(yaml.getStringList("blacklist"));
                this.bestToolsEnabled = yaml.getBoolean("bestToolsEnabled",false);
                this.hasSeenBestToolsMessage = yaml.getBoolean("hasSeenBestToolsMessage",false);
                this.hasSeenRefillMessage = yaml.getBoolean("hasSeenRefillMessage",false);
                this.refillEnabled = yaml.getBoolean("refillEnabled",false);
                this.hotbarOnly = yaml.getBoolean("hotbarOnly",true);
                this.swordOnMobs = yaml.getBoolean("swordOnMobs",true);
                this.favoriteSlot = yaml.getInt("favoriteSlot",main.getConfig().getInt("favorite-slot"));
                this.moveToFavorite = yaml.getBoolean("moveToFavorite", true);
                main.debug("Loaded player setting from file "+file.getPath());

                getPDCValues(player);
        }

        private void getPDCValues(Player player) {
                if(player.getPersistentDataContainer().has(DATA, DataType.FILE_CONFIGURATION)) {
                        FileConfiguration conf = player.getPersistentDataContainer().get(DATA, DataType.FILE_CONFIGURATION);
                        this.bestToolsEnabled = conf.getBoolean("bestToolsEnabled");
                        this.hasSeenBestToolsMessage = conf.getBoolean("hasSeenBestToolsMessage");
                        this.hasSeenRefillMessage = conf.getBoolean("hasSeenRefillMessage");
                        this.refillEnabled = conf.getBoolean("refillEnabled");
                        this.hotbarOnly = conf.getBoolean("hotbarOnly");
                }
        }

        // ── PDC save (LOCAL mode) ────────────────────────────────────

        private void save() {
                FileConfiguration conf = new YamlConfiguration();
                conf.set("blacklist",blacklist.toStringList());
                conf.set("bestToolsEnabled",bestToolsEnabled);
                conf.set("hasSeenBestToolsMessage",hasSeenBestToolsMessage);
                conf.set("hasSeenRefillMessage",hasSeenRefillMessage);
                conf.set("refillEnabled",refillEnabled);
                conf.set("hotbarOnly",hotbarOnly);
                player.getPersistentDataContainer().set(DATA,DataType.FILE_CONFIGURATION,conf);
        }

        // ── Default constructor (LOCAL mode — reads PDC) ─────────────

        public PlayerSetting(Player player, boolean bestToolsEnabled, boolean refillEnabled, boolean hotbarOnly, int favoriteSlot, boolean swordOnMobs) {

                this.player = player;
                this.blacklist = new Blacklist();
                this.bestToolsEnabled = bestToolsEnabled;
                this.refillEnabled = refillEnabled;
                this.hasSeenBestToolsMessage = false;
                this.hasSeenRefillMessage = false;
                this.hotbarOnly = hotbarOnly;
                this.swordOnMobs= swordOnMobs;
                this.favoriteSlot = favoriteSlot;
                getPDCValues(player);
                this.save();
        }

        // ── Database constructor (MYSQL/NETWORK — no PDC read/write) ──

        /**
         * Creates a PlayerSetting from database values. Does NOT read or write PDC.
         */
        public static PlayerSetting fromDatabase(Player player, boolean btEnabled, boolean rfEnabled,
                                                  boolean hotbarOnly, boolean seenBt, boolean seenRf,
                                                  Blacklist blacklist, int favoriteSlot, boolean moveToFavorite,
                                                  boolean swordOnMobs) {
                return new PlayerSetting(player, btEnabled, rfEnabled, hotbarOnly,
                        seenBt, seenRf, blacklist, favoriteSlot, moveToFavorite, swordOnMobs);
        }

        private PlayerSetting(Player player, boolean btEnabled, boolean rfEnabled, boolean hotbarOnly,
                              boolean seenBt, boolean seenRf, Blacklist blacklist, int favoriteSlot,
                              boolean moveToFavorite, boolean swordOnMobs) {
                this.player = player;
                this.bestToolsEnabled = btEnabled;
                this.refillEnabled = rfEnabled;
                this.hotbarOnly = hotbarOnly;
                this.hasSeenBestToolsMessage = seenBt;
                this.hasSeenRefillMessage = seenRf;
                this.blacklist = blacklist;
                this.favoriteSlot = favoriteSlot;
                this.moveToFavorite = moveToFavorite;
                this.swordOnMobs = swordOnMobs;
                // No PDC read/write — data comes from MySQL
        }

        // ── Apply remote update from Redis ───────────────────────────

        /**
         * Applies settings received from another server via Redis.
         * Does NOT trigger onChanged (avoids echo loop).
         */
        public void applyRemote(boolean btEnabled, boolean rfEnabled, boolean hotbar,
                                boolean seenBt, boolean seenRf, int favoriteSlot,
                                boolean moveToFavorite) {
                this.bestToolsEnabled = btEnabled;
                this.refillEnabled = rfEnabled;
                this.hotbarOnly = hotbar;
                this.hasSeenBestToolsMessage = seenBt;
                this.hasSeenRefillMessage = seenRf;
                if (favoriteSlot >= 0 && favoriteSlot <= 8) this.favoriteSlot = favoriteSlot;
                this.moveToFavorite = moveToFavorite;
        }

        // ── Toggle methods ───────────────────────────────────────────

        boolean toggleBestToolsEnabled() {
                bestToolsEnabled =!bestToolsEnabled;
                save();
                notifyChanged();
                return bestToolsEnabled;
        }

        boolean toggleRefillEnabled() {
                refillEnabled=!refillEnabled;
                save();
                notifyChanged();
                return refillEnabled;
        }

        boolean toggleHotbarOnly() {
                hotbarOnly=!hotbarOnly;
                save();
                notifyChanged();
                return hotbarOnly;
        }

        void setHasSeenBestToolsMessage(boolean seen) {
                if(seen== hasSeenBestToolsMessage) return;
                hasSeenBestToolsMessage = seen;
                save();
                changed = true;
        }

        void setHasSeenRefillMessage(boolean seen) {
                if(seen== hasSeenRefillMessage) return;
                hasSeenRefillMessage = seen;
                save();
                changed = true;
        }

        public void setFavoriteSlot(int favoriteSlot) {
                this.favoriteSlot = favoriteSlot;
                save();
                notifyChanged();
        }

        boolean toggleMoveToFavorite() {
                moveToFavorite = !moveToFavorite;
                save();
                notifyChanged();
                return moveToFavorite;
        }

}

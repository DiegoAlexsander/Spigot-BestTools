package de.jeff_media.BestTools;

import de.jeff_media.BestTools.network.StorageMode;
import de.jeff_media.BestTools.network.config.NetworkConfig;
import de.jeff_media.BestTools.network.database.DatabaseManager;
import de.jeff_media.BestTools.network.redis.RedisManager;
import de.jeff_media.BestTools.network.storage.LocalStorageProvider;
import de.jeff_media.BestTools.network.storage.MySQLStorageProvider;
import de.jeff_media.BestTools.network.storage.StorageProvider;
import de.jeff_media.BestTools.network.sync.DataSyncer;
import de.jeff_media.BestTools.placeholders.BestToolsPlaceholders;

import com.jeff_media.updatechecker.UpdateCheckSource;
import com.jeff_media.updatechecker.UpdateChecker;
import org.apache.commons.lang3.math.NumberUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JavaPlugin {

    {
        instance = this;
    }

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    final int configVersion = 17;

    final int mcVersion = getMcVersion();

    BestToolsHandler toolHandler;
    BestToolsUtils toolUtils;
    RefillListener refillListener;
    BestToolsListener bestToolsListener;
    PlayerListener playerListener;
    BestToolsCacheListener bestToolsCacheListener;
    FileUtils fileUtils;
    RefillUtils refillUtils;
    CommandBestTools commandBestTools;
    CommandRefill commandRefill;
    CommandBlacklist commandBlacklist;
    Messages messages;
    GUIHandler guiHandler;
    UpdateChecker updateChecker;

    public boolean debug=false;
    boolean wtfdebug=false;
    boolean measurePerformance=false;
    PerformanceMeter meter;

    public HashMap<UUID,PlayerSetting> playerSettings;
    boolean verbose = true;

    // ── Multi-server / network storage ──────────────────────────────
    private StorageProvider storageProvider;
    private StorageMode     storageMode = StorageMode.LOCAL;
    private RedisManager    redisManager;
    DataSyncer      dataSyncer;

    public StorageProvider getStorageProvider() { return storageProvider; }
    public StorageMode     getStorageMode()     { return storageMode; }
    public DataSyncer      getDataSyncer()      { return dataSyncer; }

    // ── Settings fingerprint (reset mechanism) ───────────────────────
    private int settingsFingerprint = 0;

    public int getSettingsFingerprint() { return settingsFingerprint; }

    /**
     * Increments fingerprint, resets storage, broadcasts to Redis (NETWORK mode),
     * and triggers a full plugin reload.
     */
    public void incrementFingerprint() {
        settingsFingerprint++;
        if (storageMode == StorageMode.NETWORK && dataSyncer != null) {
            dataSyncer.onResetAll(settingsFingerprint);
        } else if (storageProvider != null) {
            storageProvider.saveFingerprint(settingsFingerprint);
        }
        load(true);
    }

    /**
     * Adopts a fingerprint received from another server via Redis and triggers reload.
     */
    public void incrementFingerprintFromRemote(int fingerprint) {
        settingsFingerprint = fingerprint;
        if (storageProvider != null) {
            storageProvider.saveFingerprint(fingerprint);
        }
        load(true);
    }

    @Override
    public void onEnable() {
        load(false);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            new BestToolsPlaceholders(this).register();
        }

    }

    @Override
    public void onDisable() {
        // Shutdown network connections
        if (redisManager != null) { redisManager.shutdown(); redisManager = null; }
        if (storageProvider != null) { storageProvider.shutdown(); storageProvider = null; }
        dataSyncer = null;
    }

    public void debug(String text) {
        if(debug) getLogger().info("[Debug] "+text);
    }
    void wtfdebug(String text) {
        if(wtfdebug) getLogger().info("[D3BUG] "+text);
    }

    public PlayerSetting getPlayerSetting(Player player) {

        if(Objects.requireNonNull(playerSettings,"PlayerSettings must not be null").containsKey(player.getUniqueId())) {
            return playerSettings.get(player.getUniqueId());
        }

        // Delegate loading to the active StorageProvider
        PlayerSetting setting = storageProvider.loadPlayer(player);

        // For MYSQL / NETWORK modes, attach a save callback so every toggle is persisted immediately
        if (storageMode != StorageMode.LOCAL) {
            final UUID uid = player.getUniqueId();
            final PlayerSetting ref = setting;
            if (storageMode == StorageMode.NETWORK && dataSyncer != null) {
                setting.setOnChanged(() -> dataSyncer.onSettingChanged(uid, ref));
            } else {
                setting.setOnChanged(() -> storageProvider.savePlayer(uid, ref));
            }
        }

        playerSettings.put(player.getUniqueId(), setting);
        return setting;
    }

    public File getPlayerDataFile(UUID uuid) {
        return new File(getDataFolder()+File.separator+"playerdata"+File.separator+uuid.toString()+".yml");
    }

    void load(boolean reload) {

        getDataFolder().mkdir();
        saveDefaultConfig();
        File playerdataFolder = new File(getDataFolder()+ File.separator+"playerdata");
        playerdataFolder.mkdir();

        if(reload) {
            updateChecker.stop();
            HandlerList.unregisterAll(this);
            reloadConfig();

            // Shutdown existing network connections before re-init
            if (redisManager != null) { redisManager.shutdown(); redisManager = null; }
            if (storageProvider != null) { storageProvider.shutdown(); storageProvider = null; }
            dataSyncer = null;
        }

        if (getConfig().getInt("config-version", 0) != configVersion) {
            showOldConfigWarning();
            ConfigUpdater configUpdater = new ConfigUpdater(this);
            configUpdater.updateConfig();
        }

        loadDefaultValues();

        updateChecker = new UpdateChecker(this, UpdateCheckSource.SPIGOT, "81490")
            .setDonationLink("https://www.chestsort.de/donate")
            .suppressUpToDateMessage(true);
        toolHandler = new BestToolsHandler(this);
        toolUtils = new BestToolsUtils(this);
        refillListener = new RefillListener(this);
        bestToolsListener = new BestToolsListener(this);
        playerListener = new PlayerListener(this);
        bestToolsCacheListener = new BestToolsCacheListener((this));
        commandBestTools = new CommandBestTools(this);
        commandRefill = new CommandRefill(this);
        commandBlacklist = new CommandBlacklist(this);
        refillUtils = new RefillUtils((this));
        messages = new Messages(this);
        fileUtils = new FileUtils(this);
        playerSettings = new HashMap<>();
        guiHandler = new GUIHandler(this);

        // Initialize storage provider
        initStorageProvider();
        // Restore saved fingerprint (keeps reset state across restarts / reloads)
        settingsFingerprint = storageProvider.getStoredFingerprint();

        meter = new PerformanceMeter(this);

        getServer().getPluginManager().registerEvents(refillListener,this);
        getServer().getPluginManager().registerEvents(bestToolsListener,this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(bestToolsCacheListener,this);
        getServer().getPluginManager().registerEvents(guiHandler,this);
        Objects.requireNonNull(getCommand("besttools")).setExecutor(commandBestTools);
        Objects.requireNonNull(getCommand("besttools")).setTabCompleter(commandBestTools);
        Objects.requireNonNull(getCommand("refill")).setExecutor(commandRefill);

        if(getConfig().getBoolean("dump",false)) {
            try {
                fileUtils.dumpFile(new File(getDataFolder()+File.separator+"dump.csv"));
            } catch (IOException e) {
                getLogger().warning("Could not create dump.csv");
            }
        }

        registerMetrics();

        if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("true")) {
            updateChecker.checkEveryXHours(getConfig().getInt("check-interval")).checkNow();
        } // When set to on-startup, we check right now (delay 0)
        else if (getConfig().getString("check-for-updates", "true").equalsIgnoreCase("on-startup")) {
            updateChecker.checkNow();
        }

    }

    private void initStorageProvider() {
        storageMode = StorageMode.fromConfig(getConfig().getString(NetworkConfig.STORAGE_MODE, "LOCAL"));

        switch (storageMode) {
            case MYSQL:
            case NETWORK: {
                DatabaseManager db = new DatabaseManager(this);
                MySQLStorageProvider mysqlProvider = new MySQLStorageProvider(this, db);
                try {
                    mysqlProvider.initialize();
                    storageProvider = mysqlProvider;
                } catch (Exception e) {
                    getLogger().severe("[BestTools] Cannot connect to MySQL — falling back to LOCAL mode.");
                    getLogger().severe("[BestTools] Cause: " + e.getMessage());
                    storageMode = StorageMode.LOCAL;
                    storageProvider = new LocalStorageProvider(this);
                    break;
                }

                getLogger().info("[BestTools] Storage mode: " + storageMode);

                if (storageMode == StorageMode.NETWORK) {
                    redisManager = new RedisManager(this);
                    try {
                        redisManager.initialize();
                        dataSyncer = new DataSyncer(this, redisManager, storageProvider);
                    } catch (Exception e) {
                        getLogger().severe("[BestTools] Cannot connect to Redis — downgrading to MYSQL mode.");
                        getLogger().severe("[BestTools] Cause: " + e.getMessage());
                        redisManager.shutdown();
                        redisManager = null;
                        storageMode = StorageMode.MYSQL;
                    }
                }
                break;
            }
            case LOCAL:
            default: {
                storageProvider = new LocalStorageProvider(this);
                getLogger().info("[BestTools] Storage mode: LOCAL");
                break;
            }
        }
    }

    private void registerMetrics() {
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this,8187);
    }

    private void loadDefaultValues() {
        getConfig().addDefault("besttools-enabled-by-default",false);
        getConfig().addDefault("refill-enabled-by-default",false);
        getConfig().addDefault("hotbar-only", true);
        getConfig().addDefault("favorite-slot",8);
        getConfig().addDefault("check-interval",4);
        getConfig().addDefault("check-for-updates","true");
        getConfig().addDefault("allow-in-adventure-mode",false);
        getConfig().addDefault("dont-switch-during-battle",true);
        getConfig().addDefault("puns",false);
        getConfig().addDefault("use-sword-on-hostile-mobs",true);
        getConfig().addDefault("use-axe-as-sword",false);
        getConfig().addDefault("allow-gui", true);
        getConfig().addDefault("allow-commands", true);

        verbose = getConfig().getBoolean("verbose",true);
        debug = getConfig().getBoolean("debug",false);
        wtfdebug = getConfig().getBoolean("wtf-debug", false);
        measurePerformance = getConfig().getBoolean("measure-performance",false);

        if(getConfig().getInt("favorite-slot")>8) {
            getLogger().warning(String.format("favorite-slot was set to %d, but it must not be higher than 8. Using default value 8",getConfig().getInt("favorite-slot")));
            getConfig().set("favorite-slot",8);
        }

    }

    private void showOldConfigWarning() {
        getLogger().warning("==============================================");
        getLogger().warning("You were using an old config file. BestTools");
        getLogger().warning("has updated the file to the newest version.");
        getLogger().warning("Your changes have been kept.");
        getLogger().warning("==============================================");
    }

    // Returns 16 for 1.16, etc.
    static int getMcVersion() {
        Pattern p = Pattern.compile("^1\\.(\\d*)");
        Matcher m = p.matcher((Bukkit.getVersion()));
        int version = -1;
        while(m.find()) {
            if(NumberUtils.isCreatable(m.group(1)))
                version = Integer.parseInt(m.group(1));
        }
        return version;
    }
}

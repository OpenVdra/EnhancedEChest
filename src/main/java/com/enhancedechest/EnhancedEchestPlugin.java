package com.enhancedechest;

import com.enhancedechest.backup.BackupService;
import com.enhancedechest.config.ConfigMigrations;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.config.YamlMigrator;
import com.enhancedechest.expiry.ExpirySweeper;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.listener.EnderChestGuiListener;
import com.enhancedechest.listener.JoinMigrationListener;
import com.enhancedechest.listener.PlayerQuitListener;
import com.enhancedechest.listener.PlayerSettingsListener;
import com.enhancedechest.listener.VanillaEnderChestListener;
import com.enhancedechest.migration.AxVaultsMigrationService;
import com.enhancedechest.migration.MigrationService;
import com.enhancedechest.migration.PlayerVaultsXMigrationService;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.service.ChestOpener;
import com.enhancedechest.service.ChestSessionManager;
import com.enhancedechest.service.ChestSpillService;
import com.enhancedechest.service.ChestTransferService;
import com.enhancedechest.service.DbExecutor;
import com.enhancedechest.service.PermissionChestService;
import com.enhancedechest.service.PlayerSettingsCache;
import com.enhancedechest.service.StorageGateway;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.storage.StorageFactory;
import com.enhancedechest.update.UpdateChecker;
import com.enhancedechest.update.UpdateNotifyListener;
import com.enhancedechest.util.DurationFormat;
import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;

@Getter
public final class EnhancedEchestPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LanguageManager languageManager;
    private ContainerCodec codec;
    private EnderChestStorage storage;
    private DbExecutor dbExecutor;
    private StorageGateway storageGateway;
    private PlayerSettingsCache settingsCache;
    private ChestSessionManager sessionManager;
    private ChestSpillService spillService;
    private ChestTransferService chestTransferService;
    private PermissionChestService permissionChestService;
    private ChestOpener chestOpener;
    private ExpirySweeper expirySweeper;
    private BackupService backupService;
    private MigrationService migrationService;
    private AxVaultsMigrationService axVaultsMigrationService;
    private PlayerVaultsXMigrationService playerVaultsXMigrationService;
    private UpdateChecker updateChecker;
    private FoliaLib foliaLib;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfigFile();
        reloadConfig();

        foliaLib        = new FoliaLib(this);
        pluginConfig    = new PluginConfig(getConfig());
        codec           = new ContainerCodec();
        storage         = StorageFactory.create(pluginConfig, getDataFolder().toPath());

        try {
            storage.init();
        } catch (Exception e) {
            getSLF4JLogger().error("Failed to initialize storage, disabling plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        languageManager = new LanguageManager(this, pluginConfig.getLocale());

        // Service layer, wired bottom-up: the shared async pool, then the storage/settings wrappers
        // over it, then the dupe-safe session registry, then the item-moving and open-routing layers.
        dbExecutor     = new DbExecutor();
        storageGateway = new StorageGateway(storage, dbExecutor);
        settingsCache  = new PlayerSettingsCache(storage, dbExecutor, getSLF4JLogger());
        sessionManager = new ChestSessionManager(languageManager, codec, storage,
                getSLF4JLogger(), foliaLib, dbExecutor);
        spillService   = new ChestSpillService(sessionManager, storage, codec, storageGateway,
                pluginConfig.getTempExpiryMillis());
        chestTransferService = new ChestTransferService(sessionManager, storage, codec, storageGateway,
                languageManager, foliaLib, dbExecutor, getSLF4JLogger(), pluginConfig.getTempExpiryMillis());
        permissionChestService = new PermissionChestService(storageGateway, spillService,
                pluginConfig.isPermissionChestsEnabled(), pluginConfig.getDefaultSize());
        chestOpener    = new ChestOpener(sessionManager, storageGateway, settingsCache, storage,
                dbExecutor, languageManager, foliaLib, getSLF4JLogger(), pluginConfig.getDefaultSize(),
                permissionChestService, spillService);

        migrationService  = new MigrationService(storage, codec, getSLF4JLogger());
        axVaultsMigrationService = new AxVaultsMigrationService(storage, codec, getSLF4JLogger(),
                getDataFolder().getParentFile().toPath());
        playerVaultsXMigrationService = new PlayerVaultsXMigrationService(storage, codec, getSLF4JLogger(),
                getDataFolder().getParentFile().toPath());

        expirySweeper = new ExpirySweeper(spillService, storage, foliaLib,
                getSLF4JLogger(), pluginConfig.getExpiryCheckIntervalMillis());
        expirySweeper.start();

        backupService = new BackupService(storage, foliaLib, getSLF4JLogger(), getDataFolder().toPath(),
                pluginConfig.isBackupEnabled(), pluginConfig.getBackupIntervalMillis(),
                pluginConfig.getBackupKeep(), pluginConfig.getBackupFolder(),
                pluginConfig.getDatabaseType());
        backupService.start();
        if (pluginConfig.isBackupOnStartup()) {
            backupService.backupNowAsync();
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new VanillaEnderChestListener(chestOpener), this);
        pm.registerEvents(new EnderChestGuiListener(sessionManager, foliaLib, languageManager, pluginConfig), this);
        pm.registerEvents(new PlayerQuitListener(sessionManager, foliaLib), this);
        pm.registerEvents(new JoinMigrationListener(pluginConfig, migrationService), this);
        pm.registerEvents(new PlayerSettingsListener(settingsCache), this);

        // Preload settings for players already online (a /reload or hot-load fires no join event for
        // them); without this their first dialog open would fall back to a DB read each time.
        getServer().getOnlinePlayers().forEach(p -> settingsCache.preloadSettings(p.getUniqueId()));

        updateChecker = new UpdateChecker(getPluginMeta().getVersion(), getSLF4JLogger());
        pm.registerEvents(new UpdateNotifyListener(foliaLib, updateChecker, languageManager), this);
        updateChecker.checkAsync(foliaLib);

        initMetrics();

        printStartupBanner(getSLF4JLogger());
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (expirySweeper != null) {
            expirySweeper.stop();
        }
        if (backupService != null) {
            backupService.stop();
        }
        // Flush live sessions and pending saves first, drop the settings cache, then close the async
        // pool last — so the flush above can still dispatch its writes onto it.
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (settingsCache != null) {
            settingsCache.clear();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }
        getSLF4JLogger().info("EnhancedEchest disabled.");
    }

    public void reload() {
        migrateConfigFile();
        reloadConfig();

        String previousDbSignature = databaseSignature();
        pluginConfig.reload(getConfig());
        languageManager.reload(pluginConfig.getLocale());

        // Re-apply the runtime-tunable values to the already-running services so they take effect
        // without a restart. These touch only work started after this point, so they are dupe-safe
        // even while async saves are in flight.
        chestOpener.setDefaultSize(pluginConfig.getDefaultSize());
        spillService.setTempExpiry(pluginConfig.getTempExpiryMillis());
        chestTransferService.setTempExpiry(pluginConfig.getTempExpiryMillis());
        permissionChestService.setConfig(pluginConfig.isPermissionChestsEnabled(), pluginConfig.getDefaultSize());
        expirySweeper.reschedule(pluginConfig.getExpiryCheckIntervalMillis());
        backupService.reschedule(pluginConfig.isBackupEnabled(), pluginConfig.getBackupIntervalMillis(),
                pluginConfig.getBackupKeep());

        // Database settings are bound when the connection pool is built at startup; rebuilding it on a
        // live reload could drop connections mid-save and risk dupes, so we don't. Warn if they changed.
        if (!databaseSignature().equals(previousDbSignature)) {
            getSLF4JLogger().warn("Database settings changed in config but are still running on the "
                    + "previous connection — a full server restart is required for them to take effect.");
        }

        getSLF4JLogger().info("Configuration reloaded.");
    }

    /** Snapshot of every database-pool setting, used to detect changes that require a restart. */
    private String databaseSignature() {
        PluginConfig c = pluginConfig;
        return String.join(" ",
                c.getDatabaseType(), c.getSqliteFile(), c.getDbHost(), String.valueOf(c.getDbPort()),
                c.getDbName(), c.getDbUsername(), c.getDbPassword(), String.valueOf(c.getDbPoolSize()));
    }

    /** Registers the plugin with bStats (<a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142">...</a>). */
    private void initMetrics() {
        int pluginId = 32142;
        metrics = new Metrics(this, pluginId);

        metrics.addCustomChart(new SimplePie("storage_type",
                () -> pluginConfig.getDatabaseType().toUpperCase()));
        metrics.addCustomChart(new SimplePie("language",
                () -> pluginConfig.getLocale()));
    }

    private void migrateConfigFile() {
        YamlMigrator.migrate(
                new File(getDataFolder(), "config.yml"),
                getResource("config.yml"),
                ConfigMigrations.CONFIG,
                getSLF4JLogger()
        );
    }

    /** Human-readable auto-backup state for the startup banner. */
    private String backupStatus() {
        if (!pluginConfig.isBackupEnabled()) {
            return "OFF";
        }
        if (!storage.supportsBackup()) {
            return "UNSUPPORTED (" + pluginConfig.getDatabaseType() + ")";
        }
        return "every " + DurationFormat.formatRemaining(pluginConfig.getBackupIntervalMillis());
    }

    private void printStartupBanner(Logger log) {
        String version   = getPluginMeta().getVersion();
        String storage   = pluginConfig.getDatabaseType().toUpperCase();
        String locale    = pluginConfig.getLocale();
        String migration = pluginConfig.isMigrationEnabled() ? "ON" : "OFF";
        String backup    = backupStatus();
        String folia     = foliaLib.isFolia() ? "Folia" : "Paper";
        String sep       = "——————————————[ EnhancedEchest ]——————————————";

        log.info("> {}", sep);
        log.info(">");
        log.info(">   Version   : {}", version);
        log.info(">   Platform  : {}", folia);
        log.info(">   Storage   : {}", storage);
        log.info(">   Language  : {}", locale);
        log.info(">   Migration : {}", migration);
        log.info(">   Backup    : {}", backup);
        log.info(">");
        log.info("> {}", sep);
    }
}

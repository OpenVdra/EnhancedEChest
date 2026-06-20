package com.enhancedechest;

import com.enhancedechest.config.ConfigMigrations;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.config.YamlMigrator;
import com.enhancedechest.expiry.ExpirySweeper;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.listener.EnderChestGuiListener;
import com.enhancedechest.listener.JoinMigrationListener;
import com.enhancedechest.listener.PlayerQuitListener;
import com.enhancedechest.listener.VanillaEnderChestListener;
import com.enhancedechest.migration.MigrationService;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import com.enhancedechest.storage.StorageFactory;
import com.enhancedechest.update.UpdateChecker;
import com.enhancedechest.update.UpdateNotifyListener;
import com.tcoded.folialib.FoliaLib;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.io.File;

@Getter
public final class EnhancedEChestPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private LanguageManager languageManager;
    private ContainerCodec codec;
    private EnderChestStorage storage;
    private EnderChestService enderChestService;
    private ExpirySweeper expirySweeper;
    private MigrationService migrationService;
    private UpdateChecker updateChecker;
    private FoliaLib foliaLib;

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

        languageManager   = new LanguageManager(this, pluginConfig.getLocale());
        enderChestService = new EnderChestService(languageManager, codec, storage,
                getSLF4JLogger(), foliaLib, pluginConfig.getDefaultSize(),
                pluginConfig.getTempExpiryMillis());
        migrationService  = new MigrationService(storage, codec, getSLF4JLogger());

        expirySweeper = new ExpirySweeper(enderChestService, storage, foliaLib,
                getSLF4JLogger(), pluginConfig.getExpiryCheckIntervalMillis());
        expirySweeper.start();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new VanillaEnderChestListener(enderChestService), this);
        pm.registerEvents(new EnderChestGuiListener(enderChestService, foliaLib, languageManager), this);
        pm.registerEvents(new PlayerQuitListener(enderChestService, foliaLib), this);
        pm.registerEvents(new JoinMigrationListener(pluginConfig, migrationService), this);

        updateChecker = new UpdateChecker(getPluginMeta().getVersion(), getSLF4JLogger());
        pm.registerEvents(new UpdateNotifyListener(foliaLib, updateChecker, languageManager), this);
        updateChecker.checkAsync(foliaLib);

        printStartupBanner(getSLF4JLogger());
    }

    @Override
    public void onDisable() {
        if (expirySweeper != null) {
            expirySweeper.stop();
        }
        if (enderChestService != null) {
            enderChestService.shutdown();
        }
        if (storage != null) {
            storage.close();
        }
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }
        getSLF4JLogger().info("EnhancedEChest disabled.");
    }

    public void reload() {
        migrateConfigFile();
        reloadConfig();
        pluginConfig.reload(getConfig());
        languageManager.reload(pluginConfig.getLocale());
        getSLF4JLogger().info("Configuration reloaded.");
    }

    private void migrateConfigFile() {
        YamlMigrator.migrate(
                new File(getDataFolder(), "config.yml"),
                getResource("config.yml"),
                ConfigMigrations.CONFIG,
                getSLF4JLogger()
        );
    }

    private void printStartupBanner(Logger log) {
        String version   = getPluginMeta().getVersion();
        String storage   = pluginConfig.getDatabaseType().toUpperCase();
        String locale    = pluginConfig.getLocale();
        String migration = pluginConfig.isMigrationEnabled() ? "ON" : "OFF";
        String folia     = foliaLib.isFolia() ? "Folia" : (foliaLib.isPaper() ? "Paper" : "Spigot");
        String sep       = "——————————————[ EnhancedEChest ]——————————————";

        log.info("> {}", sep);
        log.info(">");
        log.info(">   Version   : {}", version);
        log.info(">   Platform  : {}", folia);
        log.info(">   Storage   : {}", storage);
        log.info(">   Language  : {}", locale);
        log.info(">   Migration : {}", migration);
        log.info(">");
        log.info("> {}", sep);
    }
}

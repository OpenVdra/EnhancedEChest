package com.enhancedechest.config;

import com.enhancedechest.util.DurationFormat;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public final class PluginConfig {

    // Language
    private String locale;

    // Ender chest
    private int defaultSize;

    // Temporary chests (overflow on shrink/delete/expire)
    private long tempExpiryMillis;
    private long expiryCheckIntervalMillis;

    // Database — common
    private String databaseType;

    // SQLite
    private String sqliteFile;

    // MySQL / MariaDB
    private String dbHost;
    private int dbPort;
    private String dbName;
    private String dbUsername;
    private String dbPassword;
    private int dbPoolSize;

    // Migration
    @Setter
    private boolean migrationEnabled;

    public PluginConfig(FileConfiguration config) {
        reload(config);
    }

    public void reload(FileConfiguration config) {
        locale = config.getString("language", "en_US");

        defaultSize = sanitizeSize(config.getInt("enderchest.default-size", 54));

        tempExpiryMillis          = parseDuration(config.getString("temp-enderchest.expiry", "24h"), "24h");
        expiryCheckIntervalMillis = parseDuration(config.getString("temp-enderchest.check-interval", "5m"), "5m");

        databaseType = config.getString("database.type", "sqlite");
        sqliteFile   = config.getString("database.sqlite-file", "enderchests.db");

        dbHost     = config.getString("database.host", "localhost");
        dbPort     = config.getInt("database.port", 3306);
        dbName     = config.getString("database.database", "enhancedechest");
        dbUsername = config.getString("database.username", "root");
        dbPassword = config.getString("database.password", "");
        dbPoolSize = config.getInt("database.pool-size", 10);

        migrationEnabled = config.getBoolean("migration.enabled", false);
    }

    /** Parses a duration string, falling back to {@code fallback} (and ultimately a safe value) on error. */
    private static long parseDuration(String value, String fallback) {
        try {
            return DurationFormat.parse(value);
        } catch (IllegalArgumentException e) {
            return DurationFormat.parse(fallback);
        }
    }

    /** True if size is a positive multiple of 9 and at most 54. */
    public static boolean isValidSize(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0;
    }

    /** Clamps an arbitrary configured size to the nearest valid value (9..54, multiple of 9). */
    public static int sanitizeSize(int size) {
        int rounded = Math.round(size / 9.0f) * 9;
        return Math.max(9, Math.min(54, rounded == 0 ? 9 : rounded));
    }
}

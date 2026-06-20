package com.enhancedechest.storage.sql;

import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Path;

public final class SqliteStorage extends AbstractSqlStorage {

    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS enderchests (
                player_uuid    TEXT    NOT NULL,
                chest_index    INTEGER NOT NULL,
                size           INTEGER NOT NULL,
                custom_name    TEXT,
                is_primary     INTEGER NOT NULL DEFAULT 0,
                container_data BLOB,
                migrated       INTEGER NOT NULL DEFAULT 0,
                last_updated   INTEGER NOT NULL DEFAULT 0,
                kind           INTEGER NOT NULL DEFAULT 0,
                expires_at     INTEGER,
                PRIMARY KEY (player_uuid, chest_index)
            )
            """;

    public SqliteStorage(Path dataFolder, String fileName) {
        super(buildConfig(dataFolder, fileName), INIT_SQL);
    }

    private static HikariConfig buildConfig(Path dataFolder, String fileName) {
        Path dbFile = dataFolder.resolve(fileName).toAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite is a single-writer file; one connection is both sufficient and correct.
        // Additional connections would contend on the file lock and produce SQLITE_BUSY.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("EnhancedEChest-SQLite");
        config.setConnectionTimeout(5_000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);

        return config;
    }
}

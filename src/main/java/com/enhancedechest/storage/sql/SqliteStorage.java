package com.enhancedechest.storage.sql;

import com.zaxxer.hikari.HikariConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

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
                icon           TEXT,
                PRIMARY KEY (player_uuid, chest_index)
            )
            """;

    private static final String INIT_SETTINGS_SQL = """
            CREATE TABLE IF NOT EXISTS player_settings (
                player_uuid TEXT    NOT NULL,
                edit_mode   INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid)
            )
            """;

    public SqliteStorage(Path dataFolder, String fileName) {
        super(buildConfig(dataFolder, fileName), INIT_SQL, INIT_SETTINGS_SQL);
    }

    @Override
    public boolean supportsBackup() {
        return true;
    }

    /**
     * Snapshots the database with {@code VACUUM INTO}. Unlike copying the .db file, this produces a
     * defragmented, transactionally-consistent copy even while saves are happening, so there is no
     * need to pause the plugin or risk a torn read. The target must not already exist (the caller
     * supplies a unique timestamped name).
     */
    @Override
    public void backup(Path target) throws Exception {
        // VACUUM INTO takes a string-literal path, not a bind parameter; escape embedded quotes.
        // Backslashes in Windows paths are literal inside a SQLite string, so no further escaping.
        String escaped = target.toAbsolutePath().toString().replace("'", "''");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + escaped + "'");
        }
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
        config.setPoolName("EnhancedEchest-SQLite");
        config.setConnectionTimeout(5_000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(0);

        return config;
    }
}

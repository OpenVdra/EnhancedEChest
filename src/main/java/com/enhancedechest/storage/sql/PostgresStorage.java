package com.enhancedechest.storage.sql;

import com.enhancedechest.config.PluginConfig;
import com.zaxxer.hikari.HikariConfig;

public final class PostgresStorage extends AbstractSqlStorage {

    // BYTEA maps directly to byte[] via JDBC — no size cap unlike MySQL's BLOB tiers.
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS enderchests (
                player_uuid    VARCHAR(36)  NOT NULL,
                chest_index    INTEGER      NOT NULL,
                size           INTEGER      NOT NULL,
                custom_name    VARCHAR(64),
                is_primary     SMALLINT     NOT NULL DEFAULT 0,
                container_data BYTEA,
                migrated       SMALLINT     NOT NULL DEFAULT 0,
                last_updated   BIGINT       NOT NULL DEFAULT 0,
                kind           SMALLINT     NOT NULL DEFAULT 0,
                expires_at     BIGINT,
                PRIMARY KEY (player_uuid, chest_index)
            )
            """;

    public PostgresStorage(PluginConfig config) {
        super(buildConfig(config), INIT_SQL);
    }

    private static HikariConfig buildConfig(PluginConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:postgresql://" + config.getDbHost() + ":" + config.getDbPort()
                + "/" + config.getDbName());
        hc.setUsername(config.getDbUsername());
        hc.setPassword(config.getDbPassword());
        hc.setMaximumPoolSize(config.getDbPoolSize());
        hc.setMinimumIdle(2);
        hc.setConnectionTestQuery("SELECT 1");
        hc.setPoolName("EnhancedEChest-Postgres");
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);
        return hc;
    }
}

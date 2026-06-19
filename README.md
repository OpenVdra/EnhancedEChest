# EnhancedEChest

A Paper plugin that replaces the vanilla 27-slot enderchest with a custom 54-slot GUI backed
by a persistent database. Each player's storage is saved synchronously on open and close with
a hard guarantee against item duplication.

## Features

- 54-slot double-chest-size GUI that fully replaces the vanilla enderchest
- Vanilla enderchest block interaction is cancelled for all players
- Sync-only, no-cache I/O: each open loads a fresh snapshot from the database; each close
  writes immediately. No inventory object is kept in memory between sessions.
- Item serialization uses Paper's Data Component API (`ItemContainerContents`) — enchants,
  custom names, lore, NBT, and Persistent Data Container entries are all preserved exactly
- SQLite out of the box; switchable to MySQL / MariaDB via config
- HikariCP and the MariaDB JDBC driver are bundled in the jar — no server-side installation
- Migration system to import existing vanilla enderchest contents on first join
- Brigadier-native commands with tab-completion

## Requirements

| Component | Minimum version |
|-----------|----------------|
| Paper     | 1.21.11        |
| Java      | 21             |

> Spigot and forks other than Paper are not supported. The plugin relies on Paper-specific
> APIs (Data Component API, Brigadier command registration via `LifecycleEvents`).

## Installation

1. Download `EnhancedEChest-<version>.jar` from the [Releases](../../releases) page.
2. Place it in your server's `plugins/` directory.
3. Restart the server. The plugin creates `plugins/EnhancedEChest/config.yml` and
   `plugins/EnhancedEChest/enderchests.db` (SQLite) automatically.
4. Adjust `config.yml` as needed and run `/enhancedechest reload`.

## Configuration

`plugins/EnhancedEChest/config.yml`:

```yaml
gui:
  title: "Ender Chest"          # Title shown in the GUI

database:
  type: sqlite                   # sqlite | mysql
  sqlite-file: enderchests.db    # path relative to plugin data folder (SQLite only)

  # MySQL / MariaDB (ignored when type is sqlite)
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  pool-size: 10

migration:
  enabled: false                 # set true to auto-import vanilla EC on player join
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/ec` | Open your enhanced enderchest | `ee.use` |
| `/enderchest` | Alias for `/ec` | `ee.use` |
| `/enhancedechest migrate on` | Enable migration mode | `ee.admin.migrate.toggle` |
| `/enhancedechest migrate off` | Disable migration mode | `ee.admin.migrate.toggle` |
| `/enhancedechest migrate run <player>` | Force-migrate an online player | `ee.admin.migrate.run` |
| `/enhancedechest migrate run all` | Migrate all online un-migrated players | `ee.admin.migrate.run` |
| `/enhancedechest reload` | Reload configuration | `ee.admin.reload` |
| `/ee` | Alias for `/enhancedechest` | (same as above) |

## Permissions

All permissions default to operator only (`default: op`).

| Permission | Description |
|------------|-------------|
| `ee.use` | Open the custom enderchest via block click or command |
| `ee.admin.migrate.toggle` | Toggle migration mode on or off |
| `ee.admin.migrate.run` | Run migration for a player or all online players |
| `ee.admin.reload` | Reload the plugin configuration |

## Migration

When `migration.enabled: true`, the plugin automatically imports each player's vanilla
27-slot enderchest into the first 27 slots of their custom 54-slot storage the first time
they join. The import is a single atomic operation:

1. Read vanilla enderchest contents
2. Encode and write to the database
3. Clear the vanilla enderchest
4. Set the player's `migrated` flag in the database

Items cannot exist in both locations at any point. The `migrated` flag prevents duplicate
imports on subsequent joins.

To migrate all currently online players immediately:
```
/enhancedechest migrate run all
```

Offline players are migrated automatically the next time they log in, provided
`migration.enabled` is `true`.

## Storage Backends

### SQLite (default)

No additional setup required. The database file is created at
`plugins/EnhancedEChest/enderchests.db`. The connection pool is capped at one connection
because SQLite is a single-writer format.

### MySQL / MariaDB

Set `database.type: mysql` in `config.yml` and fill in the connection details. The plugin
uses the bundled MariaDB Connector/J, which is compatible with MySQL 5.7, MySQL 8.x, and
MariaDB 10.3+.

The required table is created automatically on startup:

```sql
CREATE TABLE IF NOT EXISTS enderchests (
    player_uuid    VARCHAR(36)  NOT NULL,
    container_data MEDIUMBLOB   NOT NULL,
    migrated       TINYINT(1)   NOT NULL DEFAULT 0,
    last_updated   BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Building from Source

Requirements: Git, Java 21.

```bash
git clone https://github.com/OpenVdra/EnhancedEChest.git
cd EnhancedEChest
./gradlew shadowJar
```

The output jar is written to `build/libs/EnhancedEChest-<version>.jar`.

## Data Serialization Notes

Item data is stored using Paper's `DataComponentTypes.CONTAINER` component on a carrier
`ItemStack`, then serialised with `ItemStack#serializeAsBytes()`. This preserves the full
item state including enchantments, custom names, lore, and Persistent Data Container
entries. The stored bytes are prefixed with a 1-byte format version tag so that future
breaking changes in the Data Component API can be detected and migrated without data loss.

`DataComponentTypes.CONTAINER` and `ItemContainerContents` are marked `@Experimental` in
the Paper API. If a Paper update changes this API, the `ContainerCodec` class is the only
place that needs updating.

## License

This project is licensed under the GPLv3 License. See [LICENSE](LICENSE) for details.

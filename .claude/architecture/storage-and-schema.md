# Storage, schema, settings cache & serialization

## Storage layer

`EnderChestStorage` models ownership as **row existence**: a player owns chest `index` iff a row exists
for `(player_uuid, chest_index)`. There is no separate "owners" table. All methods are **synchronous**
and thread-agnostic — the `com.enhancedechest.service` layer is the only caller and dispatches them onto
the shared `DbExecutor` async pool (see [concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

`AbstractSqlStorage` holds all DML as plain SQL valid across SQLite, MySQL/MariaDB and PostgreSQL. Only
`CREATE TABLE` statements are dialect-specific, injected by each subclass (`SqliteStorage`,
`MysqlStorage`, `PostgresStorage`) as a `String...` of DDL run in order by `init()` (currently
`enderchests` + `player_settings`). New chest indexes are computed in Java (`MAX(chest_index)+1`), so no
dialect-specific upsert is required. Connections come from a HikariCP pool (size 1 for SQLite,
configurable otherwise). `StorageFactory` picks the backend from `config.type`.

**Rule:** all DML portable, only DDL per-dialect. Avoid `ON CONFLICT` / `ON DUPLICATE KEY`; do a portable
`UPDATE`-then-`INSERT`-if-no-row upsert instead.

## Schema: `enderchests`

| Column | Notes |
|--------|-------|
| `player_uuid` | part of PK |
| `chest_index` | part of PK; per-player 1-based index |
| `size` | slot count (multiple of 9, 9–54) |
| `custom_name` | nullable; null → default numbered title |
| `is_primary` | the player's chosen main; **zero or one** per player (set only by "Set as main") |
| `container_data` | nullable serialized bytes (`ContainerCodec`) |
| `migrated` | flag, meaningful on chest #1 only |
| `last_updated` | write timestamp |
| `kind` | `0` = NORMAL, `1` = TEMP (overflow) — see [expiry-and-temp-chests.md](expiry-and-temp-chests.md) |
| `expires_at` | nullable epoch-ms expiry; `NULL` = never. Indexed (`idx_enderchests_expires`) |
| `icon` | nullable material key (e.g. `minecraft:diamond`) of the list icon; `NULL` = default. Rendered as an Adventure sprite component in the dialogs |

Key operations: `createChest` (next index, **never** auto-primary; optional `expiresAt`), `ensureChest`
(create at a fixed index if absent — migration only, also never auto-primary), `resizeChest`,
`deleteChest` (**no survivor promotion** — if the deleted chest was the main, the player simply has no
main until they pick one), `renameChest`, `setIcon`, `setPrimary` (clear-then-set in a transaction — the
only way a chest becomes primary), `clearPrimary`, `isMigrated`/`setMigrated`, the item-moving
`spillShrink` / `spillRemove`, and the sweeper query `findExpired`. `saveChest` is **UPDATE-only** and
never touches size, name, or primary.

Primary resolution (`SQL_PRIMARY`) filters `kind = 0` and orders `is_primary DESC, chest_index ASC`, so
it returns the flagged main when one exists and otherwise the lowest-indexed NORMAL chest; temp chests
are never primary.

## Schema: `player_settings`

Per-player UI/behaviour preferences, **one row per player** (`player_uuid` PK), separate from
`enderchests` because they are per-player, not per-chest. Wide table, one typed column per setting (not
EAV/JSON) — fast, type-safe, DB-level defaults.

| Column | Notes |
|--------|-------|
| `player_uuid` | PK |
| `edit_mode` | bool (0/1, default 0) — remembers whether `/eclist` opens in edit mode across sessions |

Mapped to the `PlayerSettings` record (loaded/saved **whole**, never null — an absent row reads as
`PlayerSettings.defaults()`). `saveSettings` (whole object) and `setEditMode` (single targeted field, no
preceding read) are both portable upserts.

**To add a setting:** add a component to `PlayerSettings`, a column to all three DDLs, and a mapping in
`loadSettings`/`saveSettings`.

### Write-through settings cache (`PlayerSettingsCache`)

Settings are read on every dialog open, so they are cached in RAM keyed by UUID. `PlayerSettingsListener`
preloads on join and evicts on quit, so the map is **bounded by the online-player count**.
`loadSettingsAsync` serves from the cache (a miss falls back to a one-off DB read that is *not* cached,
keeping `preloadSettings` the sole inserter). `setEditModeAsync` is **write-through**: it updates the
cached copy in place (`computeIfPresent`, never inserts) and writes the DB immediately, so the cache
never holds dirty state and needs no shutdown flush.

**Leak-free invariant:** every entry is added by a join preload and removed by the matching quit
eviction; the join-then-immediate-quit race is closed by a post-load online re-check in `preloadSettings`
that drops an entry whose player already left. `onEnable` preloads already-online players (a `/reload`
fires no join event for them).

## Serialization (`ContainerCodec`)

Converts `ItemStack[] ⇄ byte[]`, parameterized by chest size on decode. `MAX_SIZE` is 54, `SLOT_STEP` is
9. Decode failures throw `CodecException`, which the service surfaces to the player (`chest.codec-failed`)
and refuses to open rather than risk clobbering stored data. Encoding is always synchronous; only the DB
write is async.

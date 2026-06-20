# EnhancedEChest — Architecture

This document describes how EnhancedEChest is put together. For day-to-day conventions see
[CLAUDE.md](CLAUDE.md); for end-user documentation see `docs/`.

## Overview

EnhancedEChest intercepts ender chest access and serves a custom inventory GUI backed by a database
instead of the per-player vanilla ender chest. Each player can own **multiple** ender chests, each
with its own size (9–54, multiple of 9), optional custom name, and a "primary" flag that decides which
one `/ec` and the ender chest block open.

The guiding invariant is **no item duplication**: a chest's contents exist in exactly one place at a
time, and a chest is always loaded fresh from the database on open and written back on close.

## Module map

```
com.enhancedechest
├── EnhancedEChestBootstrap   PluginBootstrap — registers Brigadier commands (COMMANDS lifecycle)
├── EnhancedEChestPlugin      JavaPlugin — wires services, listeners, startup banner, shutdown
├── command/
│   ├── EnderChestOpenCommand        /enderchest (open, #index, name), /eclist
│   └── admin/                       /ee reload | add | resize | delete | migrate run
├── gui/
│   ├── EnderChestService     open/save lifecycle, async dispatch, pending-save tracking
│   ├── EnderChestHolder      InventoryHolder carrying owner, index, size, source block
│   ├── EnderChestAnimator    ender chest block lid open/close animation
│   └── dialog/ChestDialogs   Paper Dialog API menus (list / detail / rename) — isolated here
├── listener/
│   ├── VanillaEnderChestListener    right-click → open custom GUI + lid animation
│   ├── EnderChestGuiListener        on close → save + close animation
│   ├── PlayerQuitListener           on quit → save + close animation
│   └── JoinMigrationListener        auto-migrate on join when enabled
├── storage/
│   ├── EnderChestStorage     interface (synchronous, single-row-per-chest ownership model)
│   ├── StorageFactory        picks backend from config.type
│   └── sql/                  AbstractSqlStorage + Sqlite / Mysql / Postgres subclasses
├── serialization/ContainerCodec     ItemStack[] ⇄ byte[] (parametric on size)
├── migration/MigrationService       vanilla EC → chest #1 import (atomic, single-location)
├── model/                    EnderChestData, ChestSummary (records)
├── lang/LanguageManager      locale loading, MiniMessage/legacy auto-detect, titles
├── config/                   PluginConfig, ConfigMigrations, YamlMigrator
└── update/                   UpdateChecker + UpdateNotifyListener
```

## Lifecycle

1. **Bootstrap** (`EnhancedEChestBootstrap`): runs before the plugin is enabled; registers the `/ec`
   and `/enhancedechest` command trees against the Brigadier `Commands` registrar. Commands are
   gated by permission via `.requires(...)`.
2. **Enable** (`EnhancedEChestPlugin#onEnable`): loads config + language, constructs the storage
   backend (`StorageFactory.create`) and calls `init()` (creates schema), builds the
   `ContainerCodec`, `EnderChestService`, registers listeners, kicks off the async update check, and
   prints a startup banner noting the detected platform (Folia / Paper / Spigot).
3. **Disable** (`onDisable`): `EnderChestService.shutdown()` flushes all pending DB saves
   (blocking up to 30s) and shuts down the executor **before** `storage.close()`.

## Open / save flow (dupe-safety)

This is the most important part of the system and must not be regressed.

### Opening

`EnderChestService.open(player, sourceBlock)` (and `openChest(index)`):

1. Hop onto the player's entity thread (`foliaLib.runAtEntity`).
2. Close any existing custom GUI synchronously (`closeExistingGui`) — its close triggers a save.
3. Resolve the chest index (primary, bootstrapping chest #1 via `createChest` if the player owns none).
4. **Wait** for any in-flight save of *that same* `(owner, index)` to finish (`waitPending`), so a
   reopen can never read pre-save data.
5. Load the row from the DB on the async executor.
6. Back on the entity thread, build an inventory with an `EnderChestHolder` and decode the bytes into it.

### Saving

`EnderChestService.save(holder, inventory)`:

1. **Encode** the contents to bytes **synchronously** on the calling (entity) thread — fast, and
   guarantees the snapshot reflects the inventory at close time.
2. Submit the DB write to the async executor, storing the `CompletableFuture` in `pendingSaves`
   keyed by `SaveKey(owner, index)`. The future removes itself on completion.

`waitPending` + this map are what let an open wait for a prior close's write to land. Encoding
failures abort the save (data is **not** written) to avoid corrupting stored bytes.

### Threading summary

- Storage methods are **synchronous** and thread-agnostic (see `EnderChestStorage` Javadoc).
- `EnderChestService` is the **only** dispatcher onto `asyncExecutor` (a daemon cached thread pool
  named `EnhancedEChest-db`).
- Anything touching the player/inventory/block runs on the appropriate region thread via FoliaLib.

## Storage layer

`EnderChestStorage` models ownership as **row existence**: a player owns chest `index` iff a row
exists for `(player_uuid, chest_index)`. There is no separate "owners" table.

`AbstractSqlStorage` holds all DML as plain SQL valid across SQLite, MySQL/MariaDB, and PostgreSQL.
Only the `CREATE TABLE` statement is dialect-specific and is injected by each subclass
(`SqliteStorage`, `MysqlStorage`, `PostgresStorage`). New chest indexes are computed in Java
(`MAX(chest_index)+1`), so no dialect-specific upsert is required. Connections come from a HikariCP
pool (pool size 1 for SQLite, configurable otherwise).

### Schema (`enderchests`)

| Column | Notes |
|--------|-------|
| `player_uuid` | part of PK |
| `chest_index` | part of PK; per-player 1-based index |
| `size` | slot count (multiple of 9, 9–54) |
| `custom_name` | nullable; null → default numbered title |
| `is_primary` | which chest `/ec` opens; exactly one per player |
| `container_data` | nullable serialized bytes (`ContainerCodec`) |
| `migrated` | flag, meaningful on chest #1 only |
| `last_updated` | write timestamp |
| `kind` | `0` = NORMAL, `1` = TEMP (overflow chest) — see *Expiry & temporary chests* |
| `expires_at` | nullable epoch-ms expiry; `NULL` = never. Indexed (`idx_enderchests_expires`) |

Key operations: `createChest` (next index, first **normal** chest auto-primary; optional `expiresAt`
for an expiring granted chest), `ensureChest` (create at a fixed index if absent — used by migration),
`resizeChest`, `deleteChest` (promotes the lowest-index **NORMAL** survivor to primary if the deleted
one was primary), `renameChest`, `setPrimary` (clear-then-set in a transaction), `isMigrated`/`setMigrated`,
plus the item-moving `spillShrink` / `spillRemove` and the sweeper query `findExpired`. `saveChest` is
**UPDATE-only** and never touches size, name, or primary. Primary resolution (`SQL_PRIMARY`) and
promotion (`SQL_MIN_INDEX`) both filter `kind = 0`, so temp chests are never primary.

## Serialization

`ContainerCodec` converts `ItemStack[] ⇄ byte[]`, parameterized by chest size on decode. `MAX_SIZE`
is 54 and `SLOT_STEP` is 9. Decode failures throw `CodecException`, which the service surfaces to the
player (`chest.codec-failed`) and refuses to open rather than risk clobbering stored data.

## Expiry & temporary chests

Chests can expire, and items that no longer fit anywhere spill into **temporary chests** instead of
being lost silently.

- **Temp chest (`kind = TEMP`)** — an overflow holder created automatically when items are cut off by
  a shrink, a non-`force` delete, or a normal chest expiring with items inside. It always carries an
  `expires_at` (config `temp-enderchest.expiry`, default `24h`), is never primary, and cannot be
  renamed or set-as-main in the dialog (Open + Back only). It **auto-deletes the moment it is emptied**
  (`save()` deletes the row instead of persisting an empty temp), and on expiry it is hard-deleted with
  any remaining items **permanently lost**.
- **Expiring normal chest** — `/ee add <player> <size> <duration>` grants a `kind = NORMAL` chest with
  an `expires_at`. On expiry its items spill into a temp chest, then the chest is removed.

**Sweeper** (`expiry/ExpirySweeper`): a FoliaLib async repeating timer at `temp-enderchest.check-interval`
(default `5m`). Each tick runs `findExpired(now)` (one indexed query on a column that is `NULL` for
almost every row) and routes each hit through the service — NORMAL → `removeChest(..., force=false)`
(spill), TEMP → `removeChest(..., force=true)` (discard). Expiry is deliberately swept, **not** lazy on
access, so the hot open/close path stays free of expiry filtering and the dangerous mutation is
centralised in one serialized place.

**Dupe-safety** (extends the open/save model): every item-moving op — shrink spill, delete spill,
normal-chest expiry spill, temp auto-delete, temp expiry — goes through `EnderChestService`:

1. `forceCloseIfOpen(owner, index)` closes the owner's GUI of the affected chest if online & open,
   which fires its `save()` synchronously (registering a pending future) before the op proceeds.
2. `runExclusive(owner, index, dbWork)` chains the work behind any pending save/op for that key via
   `pendingSaves.compute` and registers its own marker, so a concurrent `open` waits for it.
3. The actual row changes happen in **one transaction** (`spillShrink`: UPDATE original + INSERT temp;
   `spillRemove`: INSERT temp + DELETE original + promote primary). The temp index is `MAX(chest_index)+1`
   computed inside that same transaction, so items never exist in two rows visible to any outside reader.

Encoding stays synchronous (`ContainerCodec`); only the DB write runs on the async executor, as
everywhere else. `DurationFormat` (`util/`) parses the time strings (`20s`, `5m`, `1h`, `1d_2h_30m`;
units `s m h d w mo y`, with `mo = 30d` and `y = 365d`) and formats the static "expires in" snapshot
shown on dialog buttons (a live ticking countdown is impossible with the static Dialog API).

## Multi-chest UI (Dialog API)

`gui/dialog/ChestDialogs` isolates Paper's experimental Dialog API. Three dialogs:

- **list** — one button per owned chest → opens the detail dialog
- **detail** — Open / Rename / Set-as-main / Back (temp chests show only Open / Back, plus an
  "expires in" snapshot on the Open button)
- **rename** — text input + Save / Cancel (separate dialog, no inline input on detail)

Navigation avoids cursor recentering: forward transitions use client-side
`DialogAction.staticAction(ClickEvent.showDialog(child))` (child dialogs are built first so parents can
reference them), while Back / Cancel / post-mutation paths re-query the DB and re-push via
`player.showDialog`. Dialog label text lives in `gui.yml` under `dialog:` (not `messages.yml`).

## Migration

`MigrationService.migrateOnline(player)` imports a player's 27-slot vanilla ender chest into their
EnhancedEChest chest #1, in a single main-thread tick: ensure chest #1 exists at full size → copy
vanilla contents into its head slots → save to DB → clear the vanilla EC → set the `migrated` flag.
There is never a window where the items exist in both places. Each player migrates once
(`isMigrated` guard). Triggered automatically on join (when `migration.enabled`) via
`JoinMigrationListener`, or manually with `/ee migrate run <player>|all`.

## Config & language

`PluginConfig` reads `config.yml` (language, `enderchest.default-size`, the `temp-enderchest` block
parsed via `DurationFormat`, database block, migration flag) and provides `isValidSize` /
`sanitizeSize` (multiple of 9, clamped 9–54). `ConfigMigrations`
defines key-rename rules applied by `YamlMigrator` on load so existing config/language files upgrade
without manual edits.

`LanguageManager` loads `language/<locale>/{messages,gui}.yml`, falling back to `en_US` if the locale
is missing. `parse()` auto-detects MiniMessage (string contains `<`) vs legacy `&` codes. Chest titles:
custom name shown verbatim as plain text; otherwise chest #1 uses the un-numbered `enderchest.title`
and chests 2+ use `enderchest.title-numbered` with `{index}`.

## Updates

`UpdateChecker.checkAsync` runs on a FoliaLib async task at startup; `UpdateNotifyListener` notifies
admins shortly after they join (with a clickable MiniMessage download link).

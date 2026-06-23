# EnhancedEchest — Architecture

How EnhancedEchest is put together, at a glance. The detail lives in focused topic files under
[`architecture/`](architecture/) — start there when working on a specific area. For day-to-day
conventions see [CLAUDE.md](CLAUDE.md); for end-user documentation see `docs/` (VitePress site).

## Topic index

| Read this when you touch… | File |
|---------------------------|------|
| open/save, the shared live-inventory sessions, concurrent edit, dupe-safety, threading | [architecture/concurrency-and-dupe-safety.md](architecture/concurrency-and-dupe-safety.md) |
| any command, permissions, `/ee view` (admin shared view/edit), permission-granted chests | [architecture/commands-and-permissions.md](architecture/commands-and-permissions.md) |
| the storage layer, the SQL schema, the settings cache, serialization | [architecture/storage-and-schema.md](architecture/storage-and-schema.md) |
| expiry, temporary/overflow chests, the sweeper, item-moving ops | [architecture/expiry-and-temp-chests.md](architecture/expiry-and-temp-chests.md) |
| the Dialog API menus, edit-mode persistence, inventory click/drag guards | [architecture/ui-dialogs.md](architecture/ui-dialogs.md) |
| migration, config, language, the update checker | [architecture/migration-config-language.md](architecture/migration-config-language.md) |

## Overview

EnhancedEchest intercepts ender chest access and serves a custom inventory GUI backed by a database
instead of the per-player vanilla ender chest. Each player can own **multiple** ender chests, each with
its own size (9–54, multiple of 9), optional custom name, optional icon, and a "primary" flag that
decides which one `/ec` and the ender chest block open.

The guiding invariant is **no item duplication**. A chest's contents exist in exactly one place at a
time: in memory it is backed by a single shared `Inventory` (so concurrent viewers — e.g. an owner and an
admin via `/ee view` — never dupe), and it is loaded fresh from the DB on first open and written back on
last close. **Do not regress this** — see
[architecture/concurrency-and-dupe-safety.md](architecture/concurrency-and-dupe-safety.md).

## Module map

```
com.enhancedechest
├── EnhancedEchestBootstrap   PluginBootstrap — registers Brigadier commands (COMMANDS lifecycle)
├── EnhancedEchestPlugin      JavaPlugin — wires services, listeners, startup banner, shutdown
├── command/
│   ├── EnderChestOpenCommand        /enderchest (open, #index, name), /eclist
│   └── admin/ChestAdminCommand      /ee add | resize | delete | view  (+ reload, migrate run)
├── service/
│   ├── ChestSessionManager   dupe-safety core: shared live-inventory sessions registry, attach/detach,
│   │                         pending-save tracking, runExclusive / forceCloseAll (one closed class)
│   ├── ChestOpener           open routing (/ec, /eclist, right-click, /ee view) + dialog orchestration
│   ├── ChestSpillService     item-moving ops: resize/delete spill, bulk delete, expiry disposal
│   ├── PermissionChestService grant/revoke PERM chests from permissions; reconcile-on-open
│   ├── StorageGateway        async wrappers over EnderChestStorage (list/create/rename/icon/primary)
│   ├── PlayerSettingsCache   write-through settings cache, bounded by online players
│   └── DbExecutor            shared daemon async pool (EnhancedEchest-db) for all storage dispatch
├── gui/
│   ├── EnderChestHolder      InventoryHolder carrying owner, index, size, kind
│   ├── EnderChestAnimator    ender chest block lid open/close animation (Lidded API)
│   └── dialog/ChestDialogs   Paper Dialog API menus (list / detail / rename / icon) — isolated here
├── listener/
│   ├── VanillaEnderChestListener    right-click → open custom GUI
│   ├── EnderChestGuiListener        click/drag guards (read-only, temp take-only); on close → detach
│   ├── PlayerQuitListener           on quit → detach (backstop)
│   ├── PlayerSettingsListener       preload/evict the settings cache on join/quit
│   └── JoinMigrationListener        auto-migrate on join when enabled
├── storage/
│   ├── EnderChestStorage     interface (synchronous, single-row-per-chest ownership model)
│   ├── StorageFactory        picks backend from config.type
│   └── sql/                  AbstractSqlStorage + Sqlite / Mysql / Postgres subclasses
├── serialization/ContainerCodec     ItemStack[] ⇄ byte[] (parametric on size)
├── expiry/ExpirySweeper             async sweep of expired chests
├── migration/MigrationService       vanilla EC → chest #1 import (atomic, single-location)
├── model/                    EnderChestData, ChestSummary, PlayerSettings, ChestKind (NORMAL/TEMP/PERM)
├── lang/LanguageManager      locale loading, MiniMessage/legacy auto-detect, titles
├── config/                   PluginConfig, ConfigMigrations, YamlMigrator
└── update/                   UpdateChecker + UpdateNotifyListener
```

## Lifecycle

1. **Bootstrap** (`EnhancedEchestBootstrap`): runs before enable; registers the `/ec` and
   `/enhancedechest` command trees against the Brigadier `Commands` registrar, gated by `.requires(...)`.
2. **Enable** (`EnhancedEchestPlugin#onEnable`): loads config + language, builds the storage backend
   (`StorageFactory.create`) and calls `init()` (creates schema), builds the `ContainerCodec` and the
   `service` layer (`DbExecutor` → `StorageGateway`/`PlayerSettingsCache` → `ChestSessionManager` →
   `ChestSpillService` → `PermissionChestService` → `ChestOpener`), registers listeners, preloads online
   players' settings, kicks
   off the async update check, and prints a startup banner noting the detected platform (Folia / Paper).
3. **Disable** (`onDisable`): `ChestSessionManager.shutdown()` persists every still-open session and
   flushes all pending DB saves (blocking up to 30s), then `PlayerSettingsCache.clear()` and
   `DbExecutor.shutdown()` close the pool — all **before** `storage.close()`.

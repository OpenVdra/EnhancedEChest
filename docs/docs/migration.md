# Migration

EnhancedEchest can import existing ender chest data into its own storage so nothing is lost when you install it. Three sources are supported: the **vanilla** ender chest, the **AxVaults** plugin, and the **PlayerVaultsX** plugin.

## From Vanilla Ender Chests

If your players already have items in their vanilla ender chests, EnhancedEchest can import that data.

### How It Works

When a player is migrated, their vanilla ender chest slots are copied into their EnhancedEchest **chest #1**, and the vanilla ender chest is cleared. Each player is migrated **only once** and is skipped on subsequent joins.

### Automatic Migration on Join

To migrate players automatically the first time they join after the plugin is installed, enable it in `config.yml`:

```yaml
migration:
  enabled: true
```

With this on, any un-migrated player has their vanilla ender chest imported the moment they join. Once everyone you care about has logged in, you can turn it back off.

### Manual Migration

Admins can trigger migration on demand for players who are online:

| Command | Effect |
|---------|--------|
| `/ee migrate vanilla <player>` | Migrate a single online player |
| `/ee migrate vanilla all` | Migrate every player currently online |

Both require the `enhancedechest.admin.migrate` permission. Players who are already migrated are reported as skipped.

::: warning Online players only
Migration reads the player's live vanilla ender chest, so it only works for players who are **currently online**. Offline players are migrated automatically on their next join if `migration.enabled` is `true`.
:::

### Purpur (and Paper forks) {#purpur}

Purpur's enlarged ender chest (`ender-chest.six-rows` / per-permission rows `purpur.enderchest.rows.<n>`) is supported with no extra setup. Purpur keeps it in the standard ender chest data, so migration captures all rows the player had (up to the full 54 slots), not just the first 27. Just run the migration as above while the server runs on Purpur.

## From AxVaults {#axvaults}

EnhancedEchest can import vaults from the [AxVaults](https://modrinth.com/plugin/axvaults) plugin, including all items with their custom names, lore, and enchantments. This was tested against **AxVaults 2.15.0**.

Each AxVaults vault is imported into the EnhancedEchest chest with the **same number**: vault #1 becomes chest #1, vault #2 becomes chest #2, and so on. The vault size is matched to a chest large enough to hold every item.

### Before You Start

The migration reads the AxVaults database directly, so two things matter:

- **Save AxVaults first.** AxVaults keeps open vaults in memory and only writes them to its database periodically. Run `/vaultadmin save` once so every vault is flushed to disk before you migrate.
- **Use SQLite, or stop AxVaults for H2.** AxVaults' default H2 database is locked while AxVaults is running, so it cannot be read live. Either set `database.type: sqlite` in `AxVaults/config.yml` (its `data.db` can be read while the server runs), or stop the source server first if it uses H2. EnhancedEchest auto-detects whichever AxVaults database file is present in `plugins/AxVaults`.

### Running It

| Command | Effect |
|---------|--------|
| `/ee migrate axvaults` | Import vaults for every player in the AxVaults database |
| `/ee migrate axvaults <player>` | Import vaults for a single player (online or offline) |

Both require the `enhancedechest.admin.migrate` permission. The import runs in the background and reports how many players and vaults were imported when it finishes.

::: tip Safe to re-run
A vault is **never** imported over a chest that already holds items. If a player already has a chest at that number, that vault is reported as skipped and left untouched, so running the command twice will not duplicate or overwrite anything.
:::

## From PlayerVaultsX {#playervaultsx}

EnhancedEchest can import vaults from the [PlayerVaultsX](https://www.spigotmc.org/resources/playervaultsx.45741/) plugin, including all items with their custom names, lore, and enchantments. This was tested against **PlayerVaultsX 4.4.13**.

Each PlayerVaultsX vault is imported into the EnhancedEchest chest with the **same number**: vault #1 becomes chest #1, vault #2 becomes chest #2, and so on. The vault size is matched to a chest large enough to hold every item.

### Before You Start

PlayerVaultsX stores each player's vaults in a flat file at `plugins/PlayerVaults/newvaults/<uuid>.yml` — there is no database to configure. EnhancedEchest reads those files directly:

- **Vaults are saved when closed.** PlayerVaultsX writes a vault to disk when the player closes it, so make sure no one is sitting in an open vault during the migration. Restarting the source server (or simply having players log out) flushes everything to disk.
- **Run on a modern Paper server.** EnhancedEchest decodes PlayerVaultsX items using Paper's item format. Vault data created on a modern Paper server (1.20.6+) imports cleanly; data written long ago by an old Spigot server uses a different internal format and may not decode.

The plugin folder is named `PlayerVaults` (the PlayerVaultsX jar's plugin name), so EnhancedEchest looks in `plugins/PlayerVaults`. A `plugins/PlayerVaultsX` folder is also accepted as a fallback.

### Running It

| Command | Effect |
|---------|--------|
| `/ee migrate playervaultsx` | Import vaults for every player with PlayerVaultsX data |
| `/ee migrate playervaultsx <player>` | Import vaults for a single player (online or offline) |

Both require the `enhancedechest.admin.migrate` permission. The import runs in the background and reports how many players and vaults were imported when it finishes.

::: tip Safe to re-run
A vault is **never** imported over a chest that already holds items. If a player already has a chest at that number, that vault is reported as skipped and left untouched, so running the command twice will not duplicate or overwrite anything.
:::

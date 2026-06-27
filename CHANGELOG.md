# Changelog

All notable changes to EnhancedEchest are recorded here, newest first.

## 1.0.2 - 2026-06-27

This release focuses on importing existing data from other vault plugins, and adds an account-transfer command plus an admin tool to empty a chest.

### Added

- Added migration from the `AxVaults` plugin. Items keep their custom names, lore, and enchantments, and each vault maps to the EnhancedEchest chest with the same number.
  - `/ee migrate axvaults` imports every player in the AxVaults database.
  - `/ee migrate axvaults <player>` imports a single player, online or offline.
- Added migration from the `PlayerVaultsX` plugin, read directly from its vault files. Works for offline players.
  - `/ee migrate playervaultsx` imports every player with PlayerVaultsX data.
  - `/ee migrate playervaultsx <player>` imports a single player, online or offline.
- Added `/ee transfer <from> <to> <#index | name | all>` to move a player's ender chests onto another account, for when someone switches accounts.
  - Use `all` to move every chest, or a `#index` or chest name to move a single one.
  - The destination ends up with exactly the source's chests: nothing from the destination account is stacked on top, and the source's chests are removed so items are never duplicated.
  - If the destination already holds items in a chest the transfer would replace, add `override` to discard them or `temp` to move them to recoverable temporary storage.
  - Gated by the new `enhancedechest.admin.transfer` permission.
- Added a **Clear chest** button to the `/ee view` menu that empties a chest's contents. It is visible only to admins with the new `enhancedechest.admin.clear` permission, is tagged with a red `(Admin)` label, and asks for confirmation before wiping anything.

### Changed

- **Breaking:** `/ee migrate run` is now `/ee migrate vanilla`, and `/ee migrate run all` is now `/ee migrate vanilla all`. Update any console scripts or command blocks that call the old name.
- **Breaking:** the migration permission node `enhancedechest.admin.migrate.run` is now `enhancedechest.admin.migrate`. Re-grant this node to your staff.
- `/ee view <player>` and `/ee view <player> <index>` now open a per-chest menu (with Open, and Clear chest for admins who have the permission) instead of opening the inventory immediately. Click Open in the menu to open the chest.

### Improved

- Improved vanilla migration on `Purpur` and Paper forks: enlarged ender chests are now imported in full, up to all 54 slots, instead of only the first 27.
- Improved the update checker so it falls back to GitHub releases when Modrinth cannot be reached, pointing players to the GitHub download instead.
- Improved how chest contents are stored so saved data is more compact.

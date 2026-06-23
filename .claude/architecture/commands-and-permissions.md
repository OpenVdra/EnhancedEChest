# Commands & permissions

Commands are registered with Paper Brigadier in `EnhancedEchestBootstrap` on the
`LifecycleEvents.COMMANDS` lifecycle (**not** in `plugin.yml`), each gated by `.requires(...)`. Logic
lives in `command/` (player) and `command/admin/` (admin). Suggestion providers are precomputed once
(they run on every keystroke).

**Player-name suggestions:** `add` / `resize` / `delete` / `view` accept **offline** targets (resolved
via `ChestAdminCommand.resolveUuid` → `OfflinePlayer#hasPlayedBefore`), so they use the `KNOWN_PLAYERS`
provider — online names first, then offline names once a prefix is typed (capped at
`MAX_PLAYER_SUGGESTIONS`, scanned only when non-empty to keep the empty state tidy and cheap). `view`'s
index suggestions (`TARGET_CHESTS`) resolve the target via `knownPlayerUuid` (cache-only, no blocking
`getOfflinePlayer(String)` web lookup), so they work for offline owners too. `migrate run` is
**online-only** (it reads the live vanilla ender chest), so it keeps the `ONLINE_PLAYERS` provider.

## Player commands (`command/EnderChestOpenCommand`)

| Command | Permission | Action |
|---------|-----------|--------|
| `/enderchest` (`/ec`) | `enhancedechest.command.open` | Open per the [routing rules](concurrency-and-dupe-safety.md#open-routing-ec-right-click) |
| `/ec <#index \| name>` | `enhancedechest.command.open` | Open a specific chest by index or custom name (miss → `chest.unknown`) |
| `/eclist` | `enhancedechest.command.open` | Always open the management dialog |

Right-clicking an ender chest block opens the GUI with **no** permission (the `command.open` node only
gates the commands). The `command.open` node also gates the dialog's "Set as main" action.

## Permission-granted chests

Players can be granted ender chests purely from permissions — no command. The node
`enhancedechest.additional_amount.<count>.slot.<size>` encodes both how many chests and their slot count
(e.g. `enhancedechest.additional_amount.2.slot.54` → two 54-slot chests). It is a **grant**, not a gate,
so it has no `op` default. Gated by `permission-chests.enabled` in `config.yml` (default `true`).

`PermissionChestService` (in `service/`) owns the logic:

- **`resolveDesired(player)`** runs on the entity thread (it reads `getEffectivePermissions()`), regex-matches
  every node, and returns `Map<size, count>`. All matching nodes **stack** (summed per size); invalid sizes
  (not a 9-multiple, >54) or `count < 1` are ignored. Empty when the feature is disabled.
- **`reconcile(owner, desired, chests)`** diffs that target against the player's current PERM chests and
  completes with the up-to-date list. **Fast path:** base chest present and the PERM multiset already
  matches → no DB writes, returns the passed-in list. Otherwise it bootstraps the inviolable base NORMAL
  chest first, **keeps** PERM chests already at a wanted size, **resizes surplus chests in place** to fill a
  still-missing size (preserving items/name/icon via `ChestSpillService.resizeOrSpill` — only a shrink
  spills), **creates** any remaining missing sizes (`createPermChest`), and **removes** true surplus with
  its items spilled to a temp chest (highest index first). A per-owner `ConcurrentHashMap` guard skips a
  concurrent reconcile.

`ChestOpener` calls reconcile on every `/ec` / right-click open and on `/eclist`, reusing the list it already
fetched (see [concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md#open-routing-ec-right-click)).
To a player a PERM chest is indistinguishable from a NORMAL one (open/rename/icon/set-main, numbered label,
no tag); only admin commands skip it. Losing a node removes exactly those chests again, spilling items to a
recoverable temp chest; the base chest is never created/deleted/overridden by permissions. Disabling the
feature stops syncing but leaves existing PERM chests in place. No schema change — PERM reuses `kind = 2`.

## Admin commands (`/enhancedechest`, alias `/ee` — `command/admin/`)

Root requires `enhancedechest.admin`; each subcommand additionally requires its own node (Brigadier
checks both).

| Subcommand | Permission | Action |
|-----------|-----------|--------|
| `reload` | `enhancedechest.admin.reload` | Reload config + language (`ReloadCommand`) |
| `migrate run <player>\|all` | `enhancedechest.admin.migrate.run` | Import vanilla EC → chest #1 (`MigrateRunCommand`, see [migration-config-language.md](migration-config-language.md)) |
| `add <player> <size> [count] [duration]` | `enhancedechest.admin.add` | Grant chest(s); `duration` makes them expire (`ChestAdminCommand.add`) |
| `resize <player> <index> <size>` | `enhancedechest.admin.resize` | Resize, spilling cut-off items to a temp chest. **Rejected on a PERM chest** (`admin.cannot-modify-perm`) |
| `delete <player> <count> [force]` | `enhancedechest.admin.delete` | Delete the `count` newest **NORMAL** chests; first chest always kept; `force` discards items. PERM chests are skipped |
| **`view <player> [list \| index]`** | **`enhancedechest.admin.view`** | **Open another player's chest, sharing the live session** |

### `/ee view` (`ChestAdminCommand.view` / `viewList`)

Opens a target player's chest for the admin by joining the **shared session** (`service.adminOpen`), so
the admin sees — and edits — the *same* inventory the owner has open. No dupe is possible (see
[concurrency-and-dupe-safety.md](concurrency-and-dupe-safety.md)).

- **No argument** — the command lists the target's chests and routes: 0 → `admin.view-no-chests`,
  1 → open it directly, 2+ → the **admin picker dialog** (`ChestDialogs.adminViewListDialog` via
  `service.showAdminViewList`), whose buttons call `adminOpen` for the target's UUID (not the clicker's).
- **`list`** — always show the picker dialog, even for a single chest (`viewList`).
- **`<index>`** — open that chest directly, verified to exist first (`admin.chest-not-found` otherwise).
- **Offline owners are supported** — the admin becomes the sole viewer; the chest persists on close.

The picker dialog is **view-only metadata**: just open buttons (no edit-mode/rename/set-main, which are
owner operations) — see [ui-dialogs.md](ui-dialogs.md).
- **Read-only vs editable** is decided per-click in `EnderChestGuiListener`, not by this command:
  - `enhancedechest.admin.view` → may open and **look** (every item move is cancelled, `chest.view-only`).
  - `enhancedechest.admin.edit` → may **take/add** items.
  An admin viewing their **own** chest is the owner and is never restricted.
- **Folia caveat:** only one live viewer per chest, so an admin viewing an online player's open chest is
  denied (and vice-versa). On Paper, concurrent editing is fully supported.
- `index` suggestions come from `TARGET_CHESTS`, which lists the target's chests when they are online.

## Permission summary

```
enhancedechest.command.open       open /ec, /eclist, "set as main"
enhancedechest.additional_amount.<count>.slot.<size>
                                  grant <count> chests of <size> slots (stacks; no op default)
enhancedechest.admin              root for all admin subcommands
enhancedechest.admin.reload
enhancedechest.admin.migrate.run
enhancedechest.admin.add
enhancedechest.admin.resize
enhancedechest.admin.delete
enhancedechest.admin.view         open another player's chest (read-only by default)
enhancedechest.admin.edit         additionally take/add items while viewing
```

All admin nodes default to `op`.

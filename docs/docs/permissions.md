# Permissions

All nodes default to `op`. Grant them through your permission plugin (LuckPerms, etc.) to open access to other ranks.

## Player

**`enhancedechest.command.open`**
Allows using `/ec` and `/eclist` by command, and setting a main chest. Right-clicking an ender chest block never requires this.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Grants extra chests by rank. For example, `enhancedechest.additional_amount.2.slot.54` gives the player two 54-slot chests. Multiple nodes stack. Removing a node removes those chests; any items spill to a temporary chest recoverable from `/eclist`.

See [Permission-Granted Chests](#permission-granted-chests) below for full details.

## Admin

Each `/ee` command requires only its own node. There is no separate base permission:

**`enhancedechest.admin.add`** - `/ee add`: give a player a new chest.

**`enhancedechest.admin.resize`** - `/ee resize`: change a chest's slot count.

**`enhancedechest.admin.delete`** - `/ee delete`: delete a player's newest chests.

**`enhancedechest.admin.view`** - `/ee view`: open another player's chest (read-only).

**`enhancedechest.admin.edit`** - combined with `admin.view`, allows moving items.

**`enhancedechest.admin.clear`** - shows the red **(Admin) Clear chest** button in the `/ee view` menu and allows emptying a chest with it.

**`enhancedechest.admin.transfer`** - `/ee transfer`: move a player's chests onto another account.

**`enhancedechest.admin.reload`** - `/ee reload`: reload config and language files.

**`enhancedechest.admin.migrate`** - `/ee migrate vanilla`, `/ee migrate axvaults`, and `/ee migrate playervaultsx`: import data from vanilla ender chests, the AxVaults plugin, or the PlayerVaultsX plugin.

::: tip
To grant full admin access in one go, give `enhancedechest.admin.*` (if your permission plugin supports wildcards).
:::

## Permission-Granted Chests {#permission-granted-chests}

Use `enhancedechest.additional_amount.<count>.slot.<size>` to tie chest perks to ranks without using commands.

- **`<count>`**: number of chests to grant.
- **`<size>`**: slot count, a multiple of 9 from 9 to 54.
- **Nodes stack**: granting `...1.slot.9` and `...2.slot.9` gives the player three 9-slot chests total.
- **Revocation is clean**: losing a node removes those chests; items spill to a recoverable temporary chest.
- **The base chest is always kept**: grants never delete or override the player's first chest.
- Grants sync on the player's next chest open - no relog needed.

::: warning
Permission grants only apply while `permission-chests.enabled: true` in `config.yml`. Disabling it stops syncing but leaves already-granted chests in place.
:::

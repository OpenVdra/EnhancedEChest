# Permissions

EnhancedEchest uses Brigadier permission gates on every command. **All** nodes default to `op`, so
out of the box only operators can use the commands — grant the nodes below through your permission
plugin (LuckPerms, etc.) to open them up to other ranks.

## Player permission

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.command.open" defaultVal="op">
Open the ender chest by command (<code>/enderchest</code>, <code>/ec</code>, <code>/eclist</code>) and use the <strong>Set as main</strong> action in the menu. Right-clicking an ender chest <em>block</em> never requires this — but a player without it who owns several chests can never set a main, so block right-click always opens the management menu for them.
</PermRow>

<PermRow permission="enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;" defaultVal="—">
Grant extra ender chests automatically. The node encodes both how many chests and their slot count — for example <code>enhancedechest.additional_amount.2.slot.54</code> grants <strong>two 54-slot chests</strong>. Multiple matching nodes <strong>stack</strong> (summed per size). Removing a node deletes exactly that many chests of that size again; any items they held spill into a temporary chest the player can recover from <code>/eclist</code>. See <a href="#permission-granted-chests">Permission-granted chests</a> below.
</PermRow>

</BaseTable>

## Permission-granted chests

Beyond the admin `/ee add` command, you can hand out ender chests purely through permissions with the
`enhancedechest.additional_amount.<count>.slot.<size>` node. This is ideal for tying chest perks to
ranks in LuckPerms (or any permission plugin).

- **`<count>`** — how many chests to grant (a positive whole number).
- **`<size>`** — their slot count: a multiple of `9` from `9` to `54`. Invalid sizes are ignored.

The grants are synced every time the player opens their ender chest, so changes take effect on their
next open — no command or relog required.

::: tip How grants behave
- **Stacking, not highest-wins.** Every matching node is added up, grouped by size. Granting both
  `enhancedechest.additional_amount.1.slot.9` and `enhancedechest.additional_amount.2.slot.9` gives the
  player **three** 9-slot chests.
- **Losing a node removes exactly those chests.** Drop a rank and the matching chests are removed
  again; if they held items, those spill into a temporary chest recoverable from `/eclist` — nothing is
  destroyed.
- **The base chest is protected.** Every player always keeps at least one regular chest. Permissions
  never create, delete, or override it.
- **They behave like normal chests.** Granted chests can be opened, renamed, given an icon, and set as
  the main chest — they carry no special tag or restriction. Only admin commands leave them alone:
  `/ee resize` and `/ee delete` will not modify a permission-granted chest.
:::

::: warning Disabling the feature
Permission grants are only applied while `permission-chests.enabled` is `true` in `config.yml`. Turning
it off stops syncing but leaves any already-granted chests in place (they keep behaving as normal
chests). See the [Configuration](/docs/configuration) page.
:::

## Admin permissions

Admin commands use a **two-key** model: every `/ee` subcommand checks the base node
`enhancedechest.admin` **and** its own specific node below. Granting a specific node alone is not
enough — the player also needs `enhancedechest.admin`. There is no inheritance between them.

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.admin" defaultVal="op">
Base permission required for <strong>every</strong> <code>/enhancedechest</code> (<code>/ee</code>) admin command, in addition to the command-specific node.
</PermRow>

<PermRow permission="enhancedechest.admin.add" defaultVal="op">
Use <code>/ee add &lt;player&gt; &lt;size&gt; [duration]</code> to give a player a new (optionally temporary) chest.
</PermRow>

<PermRow permission="enhancedechest.admin.resize" defaultVal="op">
Use <code>/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;</code> to change a chest's slot count (spilling overflow on shrink).
</PermRow>

<PermRow permission="enhancedechest.admin.delete" defaultVal="op">
Use <code>/ee delete &lt;player&gt; &lt;count&gt; [force]</code> to delete the newest chests (spilling their items, or hard-deleting with <code>force</code>); the player's first chest is always kept.
</PermRow>

<PermRow permission="enhancedechest.admin.view" defaultVal="op">
Use <code>/ee view &lt;player&gt; [index]</code> to open another player's chest. <strong>Read-only</strong> on its own — you can see the contents but not move items.
</PermRow>

<PermRow permission="enhancedechest.admin.edit" defaultVal="op">
Granted <em>on top of</em> <code>enhancedechest.admin.view</code>, lets you <strong>take and add</strong> items while viewing another player's chest. Without it, <code>/ee view</code> is look-only.
</PermRow>

<PermRow permission="enhancedechest.admin.reload" defaultVal="op">
Use <code>/ee reload</code> to reload the configuration and language files from disk.
</PermRow>

<PermRow permission="enhancedechest.admin.migrate.run" defaultVal="op">
Use <code>/ee migrate run &lt;player&gt;</code> and <code>/ee migrate run all</code> to import vanilla ender chest contents.
</PermRow>

</BaseTable>

::: tip Granting admin access in one go
To give a moderator full admin access, grant both `enhancedechest.admin` and the specific nodes they
need (or a wildcard like `enhancedechest.admin.*` if your permission plugin expands it — remember they
**also** need the plain `enhancedechest.admin` base node).
:::

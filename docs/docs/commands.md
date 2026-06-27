# Commands

## Player Commands

<CommandRow commands="/ec" :aliases="['/enderchest']" permission="enhancedechest.command.open">

Opens your ender chest. With one chest it opens directly. With several chests, it opens your **main** if one is set, or the management menu if not. Your first chest is created automatically on first use.

</CommandRow>

<CommandRow :commands="['/ec #&lt;index&gt;', '/ec &lt;name&gt;']" permission="enhancedechest.command.open">

Opens a specific chest by number (e.g. `/ec #2`) or custom name (e.g. `/ec Loot`), skipping the menu.

</CommandRow>

<CommandRow commands="/eclist" permission="enhancedechest.command.open">

Always opens the management menu, listing all your chests. From here you can open, rename, set an icon, or set a chest as your main. The main chest is marked with a gold **★**.

</CommandRow>

::: tip
Right-clicking an ender chest block follows the same routing as `/ec`. No permission is required to right-click.
:::

## Admin Commands

Each `/ee` command requires only the specific permission listed on it. There is no separate base permission. All admin nodes default to `op`.

<CommandRow commands="/ee add &lt;player&gt; &lt;size&gt; [count] [duration]" permission="enhancedechest.admin.add">

Gives a player one or more chests. `<size>` is a multiple of 9 from 9 to 54. Add a `[duration]` (e.g. `7d`, `1d_12h`) to make them temporary.

</CommandRow>

<CommandRow commands="/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;" permission="enhancedechest.admin.resize">

Changes a chest's slot count. Items in cut-off slots are spilled to a temporary chest rather than deleted.

</CommandRow>

<CommandRow commands="/ee delete &lt;player&gt; &lt;count&gt; [force]" permission="enhancedechest.admin.delete">

Deletes the newest `<count>` chests a player owns. Items are spilled to a temporary chest by default; add `force` to discard them immediately. The player's first chest is never deleted.

</CommandRow>

<CommandRow commands="/ee view &lt;player&gt; [list | index]" permission="enhancedechest.admin.view">

Opens a per-chest menu for another player's chests (works offline). From the menu you open the chest, and admins with `enhancedechest.admin.clear` also get a red **(Admin) Clear chest** button to empty it. With `enhancedechest.admin.view` alone the inventory is read-only; add `enhancedechest.admin.edit` to move items.

</CommandRow>

<CommandRow commands="/ee transfer &lt;from&gt; &lt;to&gt; &lt;#index | name | all&gt; [override | temp]" permission="enhancedechest.admin.transfer">

Moves a player's chests onto another account, for when someone switches accounts. Use `all` to move every chest (the destination ends up with exactly the source's chests), or a `#index` or chest name to move just one. If the destination already has items in a chest this would replace, add `override` to discard them or `temp` to move them to recoverable temporary storage. The source's chests are removed, so items are never duplicated.

</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">

Reloads config and language files without restarting.

</CommandRow>

<CommandRow :commands="['/ee migrate vanilla &lt;player&gt;', '/ee migrate vanilla all']" permission="enhancedechest.admin.migrate">

Imports vanilla ender chest contents into the plugin. Each player is migrated only once. Requires the player to be online.

</CommandRow>

<CommandRow :commands="['/ee migrate axvaults', '/ee migrate axvaults &lt;player&gt;']" permission="enhancedechest.admin.migrate">

Imports vaults from the AxVaults plugin into matching EnhancedEchest chests. Works for offline players and reads the AxVaults database directly. See the [Migration](/docs/migration#axvaults) page for setup.

</CommandRow>

<CommandRow :commands="['/ee migrate playervaultsx', '/ee migrate playervaultsx &lt;player&gt;']" permission="enhancedechest.admin.migrate">

Imports vaults from the PlayerVaultsX plugin into matching EnhancedEchest chests. Works for offline players and reads the PlayerVaultsX flat-file vault data directly. See the [Migration](/docs/migration#playervaultsx) page for setup.

</CommandRow>

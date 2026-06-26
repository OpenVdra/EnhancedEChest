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

All `/ee` commands require `enhancedechest.command.admin` plus the specific permission listed on each command.

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

Opens another player's chest (works offline). With `enhancedechest.admin.view` alone the inventory is read-only; add `enhancedechest.admin.edit` to move items.

</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">

Reloads config and language files without restarting.

</CommandRow>

<CommandRow :commands="['/ee migrate run &lt;player&gt;', '/ee migrate run all']" permission="enhancedechest.admin.migrate.run">

Imports vanilla ender chest contents into the plugin. Each player is migrated only once. Requires the player to be online.

</CommandRow>

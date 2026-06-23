# Commands

Every command is registered through Paper's Brigadier system, so you get full tab-completion and
inline argument hints in-game. There are two groups: **player commands** for opening and managing
your own chests, and **admin commands** (`/ee`) for managing other players' chests.

## Player commands

<div class="command-section">

<CommandRow commands="/enderchest" aliases="/ec" permission="enhancedechest.command.open">
Open your ender chest. What opens depends on how many chests you own and whether you have chosen a main chest:
<ul>
<li><strong>One chest</strong> — opens it directly.</li>
<li><strong>Several chests, with a main set</strong> — opens your <strong>main</strong> chest directly.</li>
<li><strong>Several chests, no main set</strong> — opens the management menu so you can pick one.</li>
</ul>
Your first chest is created automatically at the default size the first time you open it. A new chest is <strong>never</strong> made main automatically — you choose it yourself (see <code>/eclist</code>).
</CommandRow>

<CommandRow :commands="['/enderchest #&lt;index&gt;', '/enderchest &lt;name&gt;']" aliases="/ec" permission="enhancedechest.command.open">
Open one specific chest, skipping the menu. Pass either its number as <code>#&lt;index&gt;</code> (e.g. <code>/ec #2</code>) or its custom name (e.g. <code>/ec Loot</code>). Tab-completion suggests your own chests. An unknown index or name reports an error instead of opening a different chest.
</CommandRow>

<CommandRow commands="/eclist" permission="enhancedechest.command.open">
Always open the <strong>management menu</strong> listing all your chests — even if you have a main set. From there you can, per chest: <strong>Open</strong> it, <strong>Rename</strong> it, or <strong>Set as main</strong> (the chest that <code>/ec</code> and block right-click open directly). The main chest is marked with a gold <strong>★</strong>.
</CommandRow>

</div>

::: tip Right-clicking an ender chest block
Right-clicking a placed ender chest block opens your chest with the **same routing** as `/ec` (single
chest → direct; several + main set → main; several without a main → menu). The block itself needs **no
permission** to use; only the `enhancedechest.command.open` permission unlocks opening by command and
the ability to set a main chest. A player **without** that permission who owns several chests therefore
always lands on the management menu.
:::

## Admin commands

All admin commands live under `/enhancedechest` (alias `/ee`). Every subcommand requires the base
permission `enhancedechest.admin` **and** the command-specific permission listed on each row below.

<div class="command-section">

<CommandRow :commands="['/ee add &lt;player&gt; &lt;size&gt; [count] [duration]']" permission="enhancedechest.admin.add">
Give a player a new chest. <code>&lt;size&gt;</code> is the slot count — a multiple of <code>9</code> from <code>9</code> to <code>54</code>. The optional <code>[count]</code> creates several chests at once (defaults to <code>1</code>). The optional <code>[duration]</code> — pass it after a count, e.g. <code>/ee add Steve 54 1 7d</code> — makes them <strong>temporary chests</strong> that expire after that time (e.g. <code>7d</code>, <code>1h</code>, <code>1d_12h</code>); omit it for permanent chests. Each new chest is added at the next free index and is not made main.
</CommandRow>

<CommandRow :commands="['/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;']" permission="enhancedechest.admin.resize">
Change the slot count of an existing chest. Growing, or shrinking with no items in the cut-off slots, is a plain resize. Shrinking below occupied slots <strong>spills</strong> the overflow items into a temporary chest rather than destroying them. <strong>Permission-granted</strong> chests cannot be resized this way — their size is fixed by the granting permission.
</CommandRow>

<CommandRow :commands="['/ee delete &lt;player&gt; &lt;count&gt; [force]']" permission="enhancedechest.admin.delete">
Delete the <code>&lt;count&gt;</code> <strong>newest</strong> chests (the highest indices) a player owns. By default any items they hold are <strong>spilled</strong> into a temporary chest so nothing is lost; add the literal <code>force</code> to <strong>hard-delete</strong> them, discarding their contents immediately. The player's <strong>first chest</strong> (lowest index) is always kept, so this can never leave them with no chest — if only the first chest remains, nothing is deleted. <strong>Permission-granted</strong> chests are skipped entirely. If you delete a player's main chest, they simply have no main until they pick a new one.
</CommandRow>

<CommandRow :commands="['/ee view &lt;player&gt; [list | index]']" permission="enhancedechest.admin.view">
Open another player's ender chest yourself. The player does <strong>not</strong> need to be online. You join the <strong>same live inventory</strong> the owner sees, so there is no risk of item duplication.
<ul>
<li><strong>No argument</strong> — if they own one chest it opens directly; with several, a <strong>picker menu</strong> of their chests opens so you can choose one.</li>
<li><strong><code>list</code></strong> — always open the picker menu, even if they only own one chest.</li>
<li><strong><code>&lt;index&gt;</code></strong> — open that specific chest by number (e.g. <code>/ee view Steve 2</code>); tab-completion suggests the target's chests.</li>
</ul>
Permissions: with <code>enhancedechest.admin.view</code> alone you can <strong>look but not touch</strong> — every attempt to move an item is blocked. Add <code>enhancedechest.admin.edit</code> to also <strong>take and add</strong> items.
<br>
On <strong>Paper</strong> the admin and the player can edit the same chest at the same time. On <strong>Folia</strong> only one person can have a chest open at a time, so if the other is already using it you'll be asked to try again shortly.
</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">
Reload the configuration and language files from disk without restarting the server.
</CommandRow>

<CommandRow :commands="['/ee migrate run &lt;player&gt;', '/ee migrate run all']" permission="enhancedechest.admin.migrate.run">
Import vanilla ender chest contents into the plugin's storage — for a single online <code>&lt;player&gt;</code>, or for everyone with <code>all</code>. Each player's vanilla chest is migrated into their chest&nbsp;#1 only once.
</CommandRow>

</div>

::: tip Offline players
`/ee add`, `/ee resize`, `/ee delete` and `/ee view` all work on **offline** players (anyone who has
joined the server before) — their chests live in the database, not on the player object. Tab-completion
suggests offline names too: start typing and matching offline players appear alongside online ones
(marked *Player (offline)*; the list is capped so a huge roster can't flood the menu). Only
`/ee migrate run` requires the player to be **online**, since it reads their live vanilla ender chest.
:::

::: warning Durations
Duration units are `s` (second), `m` (minute), `h` (hour), `d` (day), `w` (week), `mo` (month, ≈30 days)
and `y` (year, ≈365 days). Combine components with underscores — e.g. `1d_12h`, `2w`, `1mo`. A month and
a year are approximations.
:::

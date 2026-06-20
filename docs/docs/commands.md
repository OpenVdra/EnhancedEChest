# Commands

Two command roots: `/enderchest` for players and `/enhancedechest` for admins. Click any command or permission to copy it.

<div style="display:flex;gap:8px;flex-wrap:wrap;margin:12px 0 24px;">
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/enderchest</code>
  <code style="padding:4px 12px;background:var(--vp-c-default-soft);color:var(--vp-c-text-2);border-radius:6px;font-weight:700;">/ec</code>
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/eclist</code>
  <code style="padding:4px 12px;background:var(--vp-c-brand-soft);color:var(--vp-c-brand-1);border-radius:6px;font-weight:700;">/enhancedechest</code>
  <code style="padding:4px 12px;background:var(--vp-c-default-soft);color:var(--vp-c-text-2);border-radius:6px;font-weight:700;">/ee</code>
</div>

::: tip
`/ec` = `/enderchest`, `/ee` = `/enhancedechest`. Right-clicking an ender chest block opens the GUI for **everyone with no permission** — permissions only gate the commands. See [Permissions](/docs/permissions) for all nodes.
:::

## Player Commands

<div class="command-section">

<CommandRow commands="/enderchest" aliases="/ec" permission="enhancedechest.command.open">
Open your <strong>main</strong> ender chest (same as right-clicking a block). Your first chest is created automatically at the default size.
</CommandRow>

<CommandRow commands="/eclist" permission="enhancedechest.command.open">
Open the management menu listing all your chests, where you can <strong>open</strong>, <strong>rename</strong>, or <strong>set</strong> any chest as your main.
</CommandRow>

<CommandRow :commands="['/enderchest #&lt;index&gt;', '/enderchest &lt;name&gt;']" permission="enhancedechest.command.open">
Open a specific chest directly by number (e.g. <code>/enderchest #2</code>) or by its custom name, spaces included (e.g. <code>/enderchest My Tools</code>). Your chests are tab-completed.
</CommandRow>

</div>

## Admin Commands

::: warning
Every admin command needs the base node `enhancedechest.admin` **plus** its own node shown below.
:::

<div class="command-section">

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">
Reload the config and language files without restarting.
</CommandRow>

<CommandRow commands="/ee add &lt;player&gt; &lt;size&gt;" permission="enhancedechest.admin.add">
Give a player a new chest (next free index). <code>&lt;size&gt;</code> is a multiple of 9 from <code>9</code> to <code>54</code>.
</CommandRow>

<CommandRow commands="/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;" permission="enhancedechest.admin.resize">
Change the slot count of a player's chest <code>&lt;index&gt;</code> to a new <code>&lt;size&gt;</code> (9–54, multiple of 9).
</CommandRow>

<CommandRow commands="/ee delete &lt;player&gt; &lt;index&gt;" permission="enhancedechest.admin.delete">
Delete a player's chest. If it was their main, another chest is promoted automatically.
</CommandRow>

<CommandRow :commands="['/ee migrate run &lt;player&gt;', '/ee migrate run all']" permission="enhancedechest.admin.migrate.run">
Import vanilla ender chest contents — for one online player or <code>all</code> online players. Already-migrated players are skipped. See <a href="/docs/migration">Migration</a>.
</CommandRow>

</div>

<style scoped>
.command-section {
  border: 1px solid var(--vp-c-border);
  border-radius: 10px;
  overflow: hidden;
  margin-top: 24px;
  background-color: var(--vp-c-bg-soft);
}
</style>

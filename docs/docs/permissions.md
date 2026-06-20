# Permissions

EnhancedEChest uses a small, layered permission system. Every node lives under the `enhancedechest.` namespace. A command (and its subcommands) only appears and runs for players who hold its base node:

- `enhancedechest.command.open` — open the ender chest GUI **by command** (`/enderchest`, `/ec`, `/eclist`).
- `enhancedechest.admin` — base permission for every `/enhancedechest` (`/ee`) admin command.

::: info Right-clicking the block needs no permission
Opening the GUI by right-clicking an ender chest **block** is available to everyone and requires no permission. The `enhancedechest.command.open` node only controls the `/enderchest` and `/eclist` commands.
:::

Click any permission node to copy it to your clipboard.

## Default Values

| Value | Meaning |
|-------|---------|
| `op` | Only server operators have this permission by default |
| `true` | All players have this permission by default |
| `false` | No players have this permission by default; must be explicitly granted |

::: tip Letting everyone open the GUI by command
By default `enhancedechest.command.open` is operator-only. To let all players open their ender chest with `/enderchest` and `/eclist`, grant `enhancedechest.command.open` to everyone through your permissions plugin (for example, set it to `true` for the default group). Note that the ender chest **block** already works for everyone regardless of this node.
:::

## Player Permissions

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.command.open" defaultVal="op">
Open the ender chest GUI <strong>by command</strong>: <code>/enderchest</code> (alias <code>/ec</code>), <code>/eclist</code>, and the direct forms <code>/enderchest #&lt;index&gt;</code> / <code>/enderchest &lt;name&gt;</code>. Without it, these commands are hidden. Right-clicking an ender chest block does <strong>not</strong> require this permission.
</PermRow>

</BaseTable>

## Admin Permissions

Admin commands need **two** nodes: the base `enhancedechest.admin` **plus** the command-specific node below. Granting the base alone does nothing — it does not inherit the children.

<BaseTable :columns="['Permission', 'Description', 'Default']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.admin" defaultVal="op">
Base node required for <strong>every</strong> <code>/enhancedechest</code> (<code>/ee</code>) command. Must be combined with one of the specific nodes below. Without it, the <code>/enhancedechest</code> command is hidden.
</PermRow>

<PermRow permission="enhancedechest.admin.reload" defaultVal="op">
Use <code>/ee reload</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.migrate.run" defaultVal="op">
Use <code>/ee migrate run</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.add" defaultVal="op">
Use <code>/ee add</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.resize" defaultVal="op">
Use <code>/ee resize</code>.
</PermRow>

<PermRow permission="enhancedechest.admin.delete" defaultVal="op">
Use <code>/ee delete</code>.
</PermRow>

</BaseTable>

# Main Configuration

The `config.yml` file lives in `plugins/EnhancedEchest/`. It controls language, chest size, the database backend, automatic backups, and migration behavior.

Click any option or category to view additional information.

::: tip Apply changes without a restart
After editing `config.yml`, run `/ee reload` in-game or from the console to apply your changes.
:::

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Language folder to load from <code>plugins/EnhancedEchest/language/</code>. The plugin ships with <code>en_US</code> (English). To add a translation, copy the <code>en_US</code> folder, rename it, translate the files inside, and set this option to the new folder name.<br><br>
See the <a href="/docs/language">Language</a> page for the full list of message keys.

</ConfigProperty>

<ConfigGroup name="enderchest">
<template #info>
Controls the ender chests themselves.
</template>

<ConfigProperty name="default-size" value="54" type="number">
Slot count of the chest that is auto-created the first time a player ever opens their ender chest. Must be a multiple of <code>9</code>, between <code>9</code> and <code>54</code>. Invalid values are rounded to the nearest valid size.<br><br>

| Value | Rows |
|-------|------|
| <code>9</code> | 1 |
| <code>18</code> | 2 |
| <code>27</code> | 3 (vanilla size) |
| <code>36</code> | 4 |
| <code>45</code> | 5 |
| <code>54</code> | 6 (double chest) |

</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="permission-chests">
<template #info>
Controls ender chests granted automatically from permissions. See the Permissions page for the node format and behavior.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
When <code>true</code>, players are granted ender chests from <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> permissions (e.g. <code>enhancedechest.additional_amount.2.slot.54</code> → two 54-slot chests). Matching nodes <strong>stack</strong>. Grants are synced each time a player opens their ender chest; losing a node removes those chests, spilling any items into a recoverable temporary chest. Players always keep their base chest. Setting this to <code>false</code> stops syncing but leaves already-granted chests in place.<br><br>
See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page for full details.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Configures where ender chest contents are stored. SQLite works out of the box with no setup. See the Database page for MySQL, MariaDB, and PostgreSQL setup.
</template>

<ConfigProperty name="type" value="sqlite" type="string">
Storage backend. Supported values: <code>sqlite</code>, <code>mysql</code>, <code>mariadb</code>, <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="sqlite-file" value="enderchests.db" type="string">
SQLite database file, relative to the plugin's data folder. Only used when <code>type</code> is <code>sqlite</code>.
</ConfigProperty>

<ConfigProperty name="host" value="localhost" type="string">
Database host. Used by <code>mysql</code>, <code>mariadb</code>, and <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Database port. Default <code>3306</code> for MySQL/MariaDB, <code>5432</code> for PostgreSQL.
</ConfigProperty>

<ConfigProperty name="database" value="enhancedechest" type="string">
Name of the database (schema) to connect to.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Database username.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Database password. Leave empty for no password.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Maximum number of pooled database connections. SQLite always uses a single connection regardless of this value.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="backup">
<template #info>
Automatically saves a copy of all ender chest data on a timer, so you can restore it if the database is ever corrupted or something goes wrong. <strong>SQLite only</strong> — if you use MySQL/MariaDB/PostgreSQL the plugin skips auto-backup (a warning is logged at startup); use your database server's own backup tools instead. Backups are taken safely while the server runs: no one is kicked and open chests keep working.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Turn automatic backups on or off.
</ConfigProperty>

<ConfigProperty name="interval" value="6h" type="string">
How often to make a backup. Examples: <code>30m</code> (every 30 minutes), <code>6h</code> (every 6 hours), <code>1d</code> (once a day). Units: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="keep" value="10" type="number">
How many backups to keep. When there are more than this, the <strong>oldest</strong> ones are deleted automatically so the folder doesn't grow forever. Use <code>0</code> to keep every backup and never delete any.
</ConfigProperty>

<ConfigProperty name="on-startup" value="false" type="boolean">
When <code>true</code>, makes one extra backup right when the server starts, in addition to the normal timer.
</ConfigProperty>

<ConfigProperty name="folder" value="backups" type="string">
Folder (inside <code>plugins/EnhancedEchest/</code>) where backup files are saved. Each file is named like <code>enderchests-20260625-143000.db</code> (the date and time it was made), so they sort oldest-to-newest.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="migration">
<template #info>
Controls automatic import of existing vanilla ender chest data. See the Migration page for the full workflow.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
When <code>true</code>, any player who has not yet been migrated has their vanilla ender chest contents imported automatically when they join. Migration runs only once per player.
</ConfigProperty>

</ConfigGroup>

</div>

## Full Example

```yaml
# EnhancedEchest configuration

# Language locale to load from language/<locale>/
language: en_US

enderchest:
  # Slot count of the chest auto-created the first time a player opens their ender chest.
  # Must be a multiple of 9, between 9 and 54. Invalid values are rounded.
  default-size: 54

permission-chests:
  # Grant ender chests from permissions of the form:
  #   enhancedechest.additional_amount.<count>.slot.<size>
  #   e.g. enhancedechest.additional_amount.2.slot.54  -> two 54-slot chests.
  # Matching permissions STACK (summed per size). Losing a permission removes those chests,
  # spilling any items into a temporary chest. Players always keep their base chest.
  enabled: true

database:
  # Storage backend: sqlite | mysql | mariadb | postgres
  type: sqlite
  # SQLite: path relative to plugin data folder
  sqlite-file: enderchests.db
  # MySQL/MariaDB default port: 3306 | Postgres default port: 5432
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  pool-size: 10

backup:
  # Automatic backups of all ender chest data (SQLite only). Taken safely while the server runs.
  enabled: true
  # How often to back up: 30m, 6h, 1d, ... (units: s m h d w mo y)
  interval: 6h
  # Keep this many recent backups; older ones are auto-deleted. 0 = keep everything.
  keep: 10
  # Also back up once on server startup.
  on-startup: false
  # Folder (inside plugins/EnhancedEchest/) for backup files.
  folder: backups

migration:
  # When true: un-migrated players have their vanilla enderchest imported on join
  enabled: false
```

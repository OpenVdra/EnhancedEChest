# Main Configuration

The `config.yml` file lives in `plugins/EnhancedEchest/`. It controls language, chest size, the storage backend, automatic backups, and migration behavior.

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
Controls ender chests granted automatically from permissions. See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page for the node format and behavior.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
When <code>true</code>, players are granted ender chests from <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> permissions. Grants are synced each time a player opens their ender chest; losing a node removes those chests, spilling any items into a recoverable temporary chest. Players always keep their base chest. Setting this to <code>false</code> stops syncing but leaves already-granted chests in place.<br><br>
See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page for full details.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Configures where ender chest contents are stored. SQLite works out of the box with no setup. See the <a href="/docs/database">Database</a> page for MySQL, MariaDB, and PostgreSQL setup.
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
Maximum number of pooled database connections. Only applies to MySQL, MariaDB, and PostgreSQL.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="backup">
<template #info>
Automatically saves a copy of all ender chest data on a timer. <strong>SQLite only</strong>: if you use MySQL/MariaDB/PostgreSQL, use your database server's own backup tools instead.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Turn automatic backups on or off.
</ConfigProperty>

<ConfigProperty name="interval" value="6h" type="string">
How often to make a backup. Examples: <code>30m</code> (every 30 minutes), <code>6h</code> (every 6 hours), <code>1d</code> (once a day). Units: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="keep" value="10" type="number">
How many backups to keep. When there are more than this, the oldest ones are deleted automatically. Use <code>0</code> to keep every backup and never delete any.
</ConfigProperty>

<ConfigProperty name="on-startup" value="false" type="boolean">
When <code>true</code>, makes one extra backup right when the server starts, in addition to the normal timer.
</ConfigProperty>

<ConfigProperty name="folder" value="backups" type="string">
Folder (inside <code>plugins/EnhancedEchest/</code>) where backup files are saved. Each file is named with the date and time it was made.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="migration">
<template #info>
Controls automatic import of existing vanilla ender chest data. See the <a href="/docs/migration">Migration</a> page for the full workflow.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
When <code>true</code>, any player who has not yet been migrated has their vanilla ender chest contents imported automatically when they join. Migration runs only once per player.
</ConfigProperty>

</ConfigGroup>

</div>

## Full Example

```yaml
# EnhancedEchest configuration

language: en_US

enderchest:
  # Slot count of the chest auto-created the first time a player opens their ender chest.
  # Must be a multiple of 9, between 9 and 54.
  default-size: 54

permission-chests:
  enabled: true

database:
  # Storage backend: sqlite | mysql | mariadb | postgres
  type: sqlite
  sqlite-file: enderchests.db
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  pool-size: 10

backup:
  enabled: true
  interval: 6h
  keep: 10
  on-startup: false
  folder: backups

migration:
  enabled: false
```

# Installation

## Requirements

Before installing EnhancedEchest, make sure your server meets these requirements:

| Requirement | Specification |
|-------------|---------------|
| **Minecraft Version** | 26.1.x only |
| **Server Software** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) or compatible Paper forks |
| **Java Version** | Java 25 |

::: warning Paper 26.1.x is required
EnhancedEchest relies on Paper-only APIs (the plugin bootstrap, Brigadier commands, and the Dialog API) and is built against the **26.1.x** API. It will **not** run on plain Spigot or CraftBukkit, nor on other Minecraft versions.
:::

::: warning Java 25 is required
The plugin is compiled for Java 25. Make sure your server runs on a Java 25 (or newer) runtime, otherwise it will fail to load.
:::

## Download

Choose your preferred download source:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/enhancedechest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://github.com/OpenVdra/EnhancedEchest/releases" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    🐙 GitHub Releases
  </a>
</div>

## Installation Steps

### 1. Install the Plugin

1. **Stop your server** completely
2. Download the latest `.jar` file from a source above
3. Place it in your server's `plugins/` folder
4. **Start your server** (avoid using `/reload`, it can cause issues)

::: tip No extra dependencies
All database drivers, the connection pool, and the scheduler are bundled inside the jar. You do **not** need to install anything else on your server.
:::

### 2. Verify Installation

Run the following in your server console or in-game to confirm the plugin loaded:

```
/plugins
```

EnhancedEchest should appear in the list with a green status. By default it runs on **SQLite**, so it works out of the box with no further setup.

### 3. Generated Files

The plugin automatically creates its files in `plugins/EnhancedEchest/`:

| File | Description |
|------|-------------|
| `config.yml` | Main configuration: chest size, database, migration |
| `enderchests.db` | SQLite database (default storage backend) |
| `language/<locale>/messages.yml` | Player-facing messages and the plugin prefix |
| `language/<locale>/gui.yml` | Inventory titles and chest-menu labels |

## Updating

1. **Download** the new version
2. **Stop** your server
3. **Replace** the old `.jar` file with the new one
4. **Start** your server

Your database and configuration are preserved across updates.

## Getting Help

If you run into issues:

1. Check your **console logs** for error messages
2. Report bugs on **[GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues)**

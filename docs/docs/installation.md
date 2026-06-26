# Installation

## Requirements

Before installing EnhancedEchest, make sure your server meets these requirements:

| Requirement | Specification |
|-------------|---------------|
| **Minecraft Version** | 26.1.x only |
| **Server Software** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) or compatible Paper forks |
| **Java Version** | Java 25 |

::: warning Paper 26.1.x is required
EnhancedEchest requires Paper or a Paper-compatible fork (Folia, Purpur). It will **not** run on CraftBukkit, nor on other Minecraft versions.
:::

::: warning Java 25 is required
The plugin is compiled for Java 25. Make sure your server runs on Java 25 or newer, otherwise it will fail to load.
:::

## Download

Choose your preferred download source:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/enhancedechest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg" alt="Spigot" style="height: 24px;">
    Spigot
  </a>
  <a href="https://hangar.papermc.io/Nighter/EnhancedEchest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

## Installation Steps

### 1. Install the Plugin

1. **Stop your server** completely
2. Download the latest `.jar` file from a source above
3. Place it in your server's `plugins/` folder
4. **Start your server** (avoid using `/reload`, it can cause issues)

::: tip No extra dependencies
Everything the plugin needs is bundled inside the jar. You do not need to install anything else on your server.
:::

### 2. Verify Installation

Run the following in your server console or in-game to confirm the plugin loaded:

```
/plugins
```

EnhancedEchest should appear in the list with a green status. By default it uses SQLite, so it works out of the box with no further setup.

### 3. Generated Files

The plugin automatically creates its files in `plugins/EnhancedEchest/`:

| File | Description |
|------|-------------|
| `config.yml` | Main configuration: chest size, storage backend, migration |
| `enderchests.db` | SQLite database (default storage) |
| `language/<locale>/messages.yml` | Player-facing messages and the plugin prefix |
| `language/<locale>/gui.yml` | Inventory titles and chest-menu labels |

## Updating

1. **Download** the new version
2. **Stop** your server
3. **Replace** the old `.jar` file with the new one
4. **Start** your server

Your data and configuration are preserved across updates.

## Getting Help

If you run into issues:

1. Check your **console logs** for error messages
2. Report bugs on **[GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues)**

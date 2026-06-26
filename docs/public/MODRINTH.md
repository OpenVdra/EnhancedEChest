**EnhancedEchest** upgrades the vanilla ender chest into a larger, persistent, multi-chest storage system. Every player gets ender chests of up to **54 slots**, can own several, and manages them from one clean in-game menu, and every chest is saved to a real database, so contents survive restarts, resets, and world wipes.

<p align="center"> <img src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" alt="An enhanced ender chest with 54 slots" width="360" /> </p>

<div align="center">

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/enhancedechest)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/EnhancedEchest)
[![Documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/documentation/ghpages_vector.svg)](https://openvdra.github.io/EnhancedEchest/)
[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_46h.png)](http://discord.com/invite/FJN7hJKPyb)

</div>

## Features

- **Up to 54 slots**: swap the 27-slot vanilla ender chest for a GUI of up to 54 slots (a full double chest). Configurable in multiples of 9, from `9` to `54`. Opens the usual way (right-click the block or `/ec`), and the block keeps its lid animation.
- **Multiple chests per player**: own several chests. With two or more, opening the ender chest shows an in-game menu (`/eclist`) to open and switch between them, **rename** them, give them **custom icons**, and pick a **main chest** that `/ec` opens directly.
- **Permission-granted chests**: grant chests by rank with `enhancedechest.additional_amount.<count>.slot.<size>` (e.g. `...2.slot.54` = two 54-slot chests). Nodes stack, sync on open, and removing one removes those chests (items spill to a recoverable temp chest). The base chest is always protected.
- **Admin tools**: manage any player's chests online or offline: `/ee add`, `/ee resize`, `/ee delete` (all item-spill safe), and `/ee view <player>` to open someone's chest read-only or editable. Temporary chests can auto-expire (e.g. `7d`, `1d_12h`).
- **Database-backed**: contents are serialized to **SQLite** (built in, zero setup), **MySQL / MariaDB**, or **PostgreSQL**. All DB work runs off the main thread on a HikariCP pool, so saving never blocks the tick.
- **Migration**: imports vanilla ender chest data automatically on join, or on demand with `/ee migrate vanilla <player>` / `all`. Also imports from the **AxVaults** plugin with `/ee migrate axvaults` (tested with AxVaults 2.15.0) and the **PlayerVaultsX** plugin with `/ee migrate playervaultsx` (tested with PlayerVaultsX 4.4.13), both supporting offline players. Each player is migrated only once.
- **Bedrock support**: menus use Paper's **Dialog API**, which [Geyser](https://geysermc.org/) converts to native Bedrock forms with no extra setup.
- **Localization**: all text lives in editable language files with full **MiniMessage** formatting.

<p align="center">
  <img src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" alt="The chest list menu showing several owned ender chests" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/76bc97fa-1dcb-4e39-8bde-9504ebc4d768" alt="A chest's management menu" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/573814dd-6f58-4e9c-b65a-58842e3ba2a2" alt="The rename prompt for a chest" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/ce6b235b-980c-4403-86d3-503c25f32d77" alt="The searchable item picker for choosing a chest icon" />
</p>

## Commands

**Player**

- `/ec`: open your main chest (or the menu if you have several and no main set)
- `/ec #<index>` / `/ec <name>`: open one specific chest directly
- `/eclist`: always open the management menu

**Admin** (`/ee`)

- `/ee add <player> <size> [count] [duration]`: give chest(s), optionally temporary
- `/ee resize <player> <index> <size>`: change a chest's slot count (overflow spills safely)
- `/ee delete <player> <count> [force]`: delete newest chests (the first is always kept)
- `/ee view <player> [list | index]`: open another player's chest, view-only or editable
- `/ee migrate vanilla <player> | all`: import vanilla ender chest data
- `/ee migrate axvaults [<player>]`: import vaults from the AxVaults plugin
- `/ee migrate playervaultsx [<player>]`: import vaults from the PlayerVaultsX plugin
- `/ee reload`: reload config and language files

## Requirements

**Minecraft** 26.1.x · **Server** Paper / Folia / Purpur (or compatible forks) · **Java** 25+

**Installation:** stop your server, drop the `.jar` into `plugins/`, and start. It runs on **SQLite out of the box**, no extra setup needed.

EnhancedEchest is completely **free and open source**. Fork it, build on it, or contribute back. ❤️

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" width="100%" />
  </a>
</p>

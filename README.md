<div align="center">

<img src="docs/public/logo.png" alt="EnhancedEchest" width="120" />

# EnhancedEchest

A Paper plugin that replaces the vanilla ender chest with a larger, multi-chest, database-backed storage system.

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/enhancedechest)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/EnhancedEchest)
[![Documentation](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/documentation/ghpages_vector.svg)](https://openvdra.github.io/EnhancedEchest/)
[![Discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_46h.png)](http://discord.com/invite/FJN7hJKPyb)

</div>

## Requirements

- Paper 1.21.11 - 26.2 (or a compatible fork such as Purpur / Folia)
- Java 21

## Installation

1. Download the latest `.jar` from [Releases](https://github.com/OpenVdra/EnhancedEchest/releases) or [Modrinth](https://modrinth.com/plugin/enhancedechest).
2. Place it in your server's `plugins/` directory.
3. Restart the server. SQLite is used by default and requires no additional setup.

## Building from source

```bash
./gradlew build
```

The output jar is placed in `build/libs/` and copied to `TestServer/plugins/` automatically via the `shadowJar` task. Adjust the destination path in `build.gradle.kts` if your test server is located elsewhere.

## Documentation

Full configuration reference, command list, permission nodes, and database setup are available at [openvdra.github.io/EnhancedEchest](https://openvdra.github.io/EnhancedEchest/).

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.

## Credits

Plugin icon by [m11.dalp.sh](https://m11.dalp.sh/).

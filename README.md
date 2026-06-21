<div align="center">

<img src="docs/public/logo.png" alt="EnhancedEchest" width="120" />

# EnhancedEchest

**Bigger ender chests, saved forever.**

A Paper plugin that gives every player larger, multi-chest ender storage backed by a real database — with zero duplication and zero data loss.

[![Docs](https://img.shields.io/badge/docs-online-8E44AD)](https://openvdra.github.io/EnhancedEchest/)
[![Modrinth](https://img.shields.io/badge/download-Modrinth-00AF5C)](https://modrinth.com/plugin/enhancedechest)
[![GitHub](https://img.shields.io/badge/source-GitHub-181717?logo=github)](https://github.com/OpenVdra/EnhancedEchest)

📖 **[Read the full documentation →](https://openvdra.github.io/EnhancedEchest/)**

</div>

---

## Features

- 📦 **Up to 54 slots** — a full double chest instead of the vanilla 27
- 🗂️ **Multiple chests per player** — open, name, and switch between them from an in-game menu
- 💾 **Database-backed** — SQLite (zero setup), MySQL, MariaDB, or PostgreSQL
- 🛡️ **No duplication** — fresh load on open, immediate save on close
- 🔄 **Migration** — import existing vanilla ender chest contents
- 🌿 **Folia ready** — runs on Paper, Folia, and Paper forks; all libraries bundled

## Requirements

- Paper 26.1.x (or Folia / a Paper fork) — this exact minor line only
- Java 25

## Quick Start

1. Download the latest `.jar` from [Releases](https://github.com/OpenVdra/EnhancedEchest/releases) or [Modrinth](https://modrinth.com/plugin/enhancedechest).
2. Drop it into your server's `plugins/` folder.
3. Restart the server. It runs on SQLite out of the box.

Players can open their ender chest by right-clicking an ender chest block straight away — no permission needed. To let them open it by command (`/enderchest`, `/eclist`), grant `enhancedechest.command.open`.

Full setup, commands, permissions, database, and migration guides are in the **[documentation](https://openvdra.github.io/EnhancedEchest/)**.

## Credits

Plugin icon by [m11.dalp.sh](https://m11.dalp.sh/).

## License

Licensed under the GPLv3 License — see [LICENSE](LICENSE) for details.

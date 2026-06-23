# EnhancedEchest — Claude guide

A Paper plugin that replaces the vanilla ender chest with a larger, multi-chest, database-backed
storage system. Players get ender chests of up to **54 slots**, can own several, and manage them
from an in-game dialog. All contents are serialized to SQLite / MySQL / MariaDB / PostgreSQL.

For the full design, read [ARCHITECTURE.md](ARCHITECTURE.md). For user-facing docs, see `docs/`.

## Build & run

```bash
./gradlew build        # compiles + runs shadowJar (the deliverable)
./gradlew shadowJar    # build the relocated fat jar only
```

- Output jar: `EnhancedEchest-<version>.jar`. `build.gradle.kts` also copies it to a local
  `TestServer/plugins` directory (`shadowJar.destinationDirectory`) — adjust that path if your
  test server lives elsewhere.
- There is no automated test suite yet; verification is done by running on a Paper/Folia server.

## Stack & constraints

- **Java 25**, **Paper API 26.1.2** (`paper-api:26.1.2.build.72-stable`; api-version `26.1.2`),
  Gradle Kotlin DSL, ShadowJar.
- Paper-only APIs are used (`paper-plugin.yml` bootstrapper, Brigadier commands, Dialog API) —
  the plugin requires **Paper** (or a Paper-compatible fork such as Purpur / Folia) and does not run
  on CraftBukkit.
- All third-party libs are **shaded and relocated** under `com.enhancedechest.libs.*`
  (HikariCP, MariaDB driver, PostgreSQL driver, FoliaLib). SQLite driver is `compileOnly`
  (Paper bundles it). Never reference these libs by their original package in new code without
  matching the relocation.
- Base package: `com.enhancedechest`.

## Conventions

- **Threading / Folia:** all scheduling goes through `FoliaLib` so the jar runs on Paper/Folia.
  `runAsync` / `runAtEntity` / `runAtLocation` take a `Consumer<WrappedTask>`, **not** a `Runnable`;
  the `*Later` variants have `Runnable` overloads. Never touch entities/blocks off their region thread.
- **DB access is synchronous** in the storage layer; the `com.enhancedechest.service` layer is the only
  place allowed to dispatch storage calls onto the async executor, and it does so through the shared
  `DbExecutor` (pool `EnhancedEchest-db`). Don't call storage from a region/main thread.
- **Dupe-safety is load-bearing** — do not "optimize" away the model: one **shared `Inventory` per open
  chest** (so concurrent viewers can't dupe), load-fresh on first open, save on **last** viewer close,
  pending-save-wait on reopen. All open paths must funnel through `ChestSessionManager.open`; session
  bookkeeping is single-threaded via `onGlobal`. The whole dupe-safety core (the `sessions` registry,
  `runExclusive`, `forceCloseAll`) lives in the one closed class `ChestSessionManager` — keep it there.
  Encoding happens sync; only the DB write is async. Full detail:
  [architecture/concurrency-and-dupe-safety.md](architecture/concurrency-and-dupe-safety.md).
- **Commands** are registered with Paper Brigadier in `EnhancedEchestBootstrap` (LifecycleEvents.COMMANDS),
  not in `plugin.yml`. Permissions default to `op`.
- **Messages:** `LanguageManager.parse()` auto-detects format per string — contains `<` → MiniMessage,
  otherwise legacy `&` codes (with `&#RRGGBB` hex). Default locale files use legacy `&` codes; the
  clickable update link stays MiniMessage. Keys live in `language/<locale>/{messages,gui}.yml`.
- **Config / language migrations:** `ConfigMigrations` + `YamlMigrator` rename keys on load so existing
  installs upgrade cleanly. Add a rename rule there rather than silently changing a key name.
- **Open routing & the "main" chest** (`ChestOpener.open`): `/ec` and right-click decide between
  opening a chest directly vs. showing the `/eclist` management dialog —
  - **0–1 chest** → open it directly (bootstrapping chest #1 if none).
  - **2+ chests + an explicit main flagged + caller has `enhancedechest.command.open`** → open the main directly.
  - **2+ chests otherwise** (no main set, or no permission) → management dialog.
  The main chest is **never auto-assigned**: `createChest`/`ensureChest` insert with `is_primary = 0` and
  deletes do not promote a survivor — it is set only by the dialog's "Set as main" (`setPrimary`). So
  `is_primary` is zero-or-one per player; `getPrimaryIndex`/`SQL_PRIMARY` falls back to the lowest NORMAL
  index when none is flagged (keeps single-chest `/ec` working). The list marks the main chest with a
  gold `★` appended to its label (`gui.yml dialog.main-tag`); dialog buttons themselves are plain text.
  Don't reintroduce auto-primary — it breaks the "user explicitly chooses their main" model.

## Docs site

`docs/` is a VitePress site deployed to GitHub Pages by `.github/workflows/deploy-docs.yml`.
`config.mts` sets `base: '/EnhancedEchest/'` for the project page — change it (and add `public/CNAME`)
if a custom domain is set up. Build locally with `cd docs && npm install && npm run docs:build`.

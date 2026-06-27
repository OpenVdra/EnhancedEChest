---
name: write-update-log
description: >
  Guidelines for writing release notes / update logs (changelogs) for EnhancedEchest, aimed at
  end users (server admins and players). Use this skill whenever the user asks to write, draft,
  or review an update log, changelog, release notes, or "what's new" text for a new version,
  including notes posted to a GitHub Release, Modrinth, Hangar, SpigotMC, or a Discord webhook.
  Pairs with [[write-enduser-docs]] for tone and formatting.
---

# Update Log Guidelines

This skill encodes how to write release notes for EnhancedEchest. The audience is the same as the
docs site: **server admins and players**, not developers reading source code. The voice, the
no-emoji and no-em-dash rules, and the user-facing focus all carry over from `write-enduser-docs`.
Read that skill too when in doubt about tone.

The goal is a changelog that a server admin can skim in thirty seconds and know exactly what
changed, what they must do, and whether they want to upgrade.

## Audience and altitude

Write each entry from the reader's point of view. The reader cares about:

- What they can now do that they could not before.
- What behaves differently, especially anything that breaks an existing command, permission, or config.
- What was broken and is now fixed.
- What action, if any, they must take to upgrade safely.

The reader does not care about internal mechanics. Apply the same exclusions as the docs:
do not mention HikariCP, FoliaLib, Brigadier, the dupe-safe load/save model, codecs, relocation,
async executors, or how data is serialized. If a change has no visible effect for the user, it
does not belong in the changelog.

**Instead of:** "Simplified null handling in ContainerCodec to drop trailing-null encoding."
**Write:** "Chest contents are stored more compactly." Or omit it if the saving is negligible.

## Deriving the content from git

Before writing, build the change list from the commit range between the last release tag and the
new one:

```bash
git log <previous-tag-or-commit>..HEAD --format="%h %s%n%b"
```

Then triage every commit into one of three buckets:

1. **User-facing** -> goes in the changelog, rephrased as a benefit (see sections below).
2. **Docs-site only** (anything touching only `docs/`, the VitePress config, components, or
   `.claude/`) -> exclude. Documentation changes are not plugin changes.
3. **Internal with no visible effect** (refactors, codec tweaks, build tooling) -> exclude, unless
   there is a real user-observable result (a measurable speedup, smaller storage, fewer errors),
   in which case state only the result.

Never copy a commit subject verbatim. Commit messages are written for contributors; changelog lines
are written for users. Translate "feat: implement AxVaults migration support" into
"Added migration from the AxVaults plugin."

## Structure

Each version is one block. Order the sections by how much they matter to the reader, dropping any
section that has no entries:

1. **Added** - new features and commands.
2. **Changed** - changes to existing behavior. Breaking changes go first within this section.
3. **Fixed** - bug fixes.
4. **Improved** - performance, compatibility, or quality gains that are not new features.
5. **Removed** - features or options taken away.

### Header line

```md
## 1.0.2 - 2026-06-27
```

Version number, a hyphen, then the ISO date (`YYYY-MM-DD`). No "v" prefix. One optional sentence
under the header only if the release has a theme worth a headline ("This release focuses on
importing data from other vault plugins."). Otherwise go straight to the sections.

### Entries

- One bullet per change. Start with a past-tense verb that matches the section: "Added", "Changed",
  "Fixed", "Improved", "Removed". Within a section the leading verb may be dropped if it reads
  cleanly, but stay consistent inside a single changelog.
- One sentence per bullet. A second sentence is allowed only to state a required upgrade action or
  an important caveat.
- Name the exact command, permission, or config key the change touches, in backticks. Users search
  the changelog for these.
- Group sub-points under a bullet with an indented list only when a single feature has several
  facets (for example, one migration source with both an all-players and a single-player command).

### Breaking changes

Any renamed or removed command, permission node, or config key is breaking. Call it out explicitly:

- Put it at the top of **Changed**.
- Prefix the bullet with **Breaking:** in bold.
- State the old name and the new name with an arrow: `` `/ee migrate run` is now `/ee migrate vanilla` ``.
- Add the one action the admin must take (update scripts, update permission grants).

## Formatting rules

These match `write-enduser-docs` exactly:

- **No emoji** anywhere in the changelog.
- **No em dash** (`-`). Use a comma, period, colon, or rewrite.
- **No horizontal rules** (`---`) between versions or sections. The `##` version header and `###`
  section headers carry the structure.
- Backticks for every command, permission node, config key, and plugin name (`AxVaults`,
  `PlayerVaultsX`, `Purpur`).
- Keep version blocks newest-first in a `CHANGELOG.md`.

## Multi-target reuse

The same text serves the GitHub Release body, Modrinth, Hangar, and a Discord post, so keep it
plain Markdown with no platform-specific syntax. For Discord, the heading levels render as bold
lines, which is fine. Do not add platform mentions or "download here" links inside the changelog
body; those belong outside it.

## Quick checklist before submitting

- [ ] Header is `## <version> - <YYYY-MM-DD>`
- [ ] Sections ordered Added, Changed, Fixed, Improved, Removed; empty ones dropped
- [ ] Breaking changes are first in Changed, bold-prefixed, with old -> new and the upgrade action
- [ ] Every command, permission, and config key is in backticks
- [ ] No commit subjects copied verbatim
- [ ] No docs-site-only or purely internal commits included
- [ ] No emoji, no em dash, no `---` rules
- [ ] One sentence per bullet, second sentence only for an upgrade action or caveat

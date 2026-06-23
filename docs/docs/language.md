# Language

All player-facing text in EnhancedEchest lives in editable language files, so you can translate or reword every message. Files are loaded from:

```
plugins/EnhancedEchest/language/<locale>/
```

The active locale is set by the `language` option in [`config.yml`](/docs/configuration) (default: `en_US`). The plugin ships with the `en_US` (English) and `vi_VN` (Tiếng Việt) locales.

## Files

| File | Contents |
|------|----------|
| `messages.yml` | The plugin prefix and all chat messages (commands, errors, admin feedback, update notices) |
| `gui.yml` | Inventory titles and the labels used in the `/eclist` management menu |

## Formatting

Color is written with legacy `&` codes, including hex colors in the form `&#RRGGBB`. Placeholders such as `{prefix}`, `{player}`, `{index}`, and `{size}` are replaced at runtime.

```yaml
prefix: '&#9B59B6EɴʜᴀɴᴄᴇᴅEᴄʜᴇsᴛ &8⏩ &r'

admin:
  chest-added: '{prefix}&aAdded Ender Chest &e{index}&a (&e{size}&a slots) to &e{player}&a.'
```

The default messages follow a simple palette:

| Color | Used for |
|-------|----------|
| `&#FF4444` | Errors |
| `&#F0C857` | Warnings / caution |
| `&a` | Success |
| `&e` / `&f` | Highlighted values |
| `&7` / `&8` | Muted text and separators |

::: tip MiniMessage is also supported
Any message that contains a `<` is parsed as [MiniMessage](https://docs.advntr.dev/minimessage/format) instead of legacy codes. This is how the update notice keeps its clickable download link (`<click:open_url:...>`).
:::

## Chest titles

`gui.yml` controls how chest inventory titles are shown:

```yaml
enderchest:
  # Title of the first chest (index 1) when it has no custom name (no number shown).
  title: 'Ender Chest'
  # Title of chests 2+ when they have no custom name. {index} is the chest number.
  title-numbered: 'Ender Chest {index}'
```

- Chest **#1** shows the un-numbered `title` ("Ender Chest")
- Chests **#2 and up** show `title-numbered` with their index
- A chest with a **custom name** (set via `/eclist` → Rename) shows that name instead

The `dialog:` section of `gui.yml` holds the button and label text for the `/eclist` menu: `open`, `rename`, `set-main`, `back`, and so on.

## Adding a translation

1. Copy the `en_US` folder inside `language/`
2. Rename the copy to your locale (for example `de_DE` or `vi_VN`)
3. Translate the text inside `messages.yml` and `gui.yml`
4. Set `language: <your-locale>` in `config.yml`
5. Run `/ee reload`

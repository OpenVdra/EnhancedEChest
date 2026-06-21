# Features ✨

Here is an overview of everything **EnhancedEchest** brings to your Minecraft server.

## 📦 Larger Ender Chests

EnhancedEchest replaces the vanilla 27-slot ender chest with a configurable GUI of up to **54 slots**.

<CardGrid>

<FeatureCard icon="🖱️" title="Same Block, More Space">

Players open their ender chest exactly the way they always have — by right-clicking an ender chest block — and get the larger inventory instead of the vanilla screen.

- Opens on right-click or via <code>/ec</code>
- The real ender chest block keeps its open/close lid animation
- Size is configurable in multiples of 9, from 9 up to 54

</FeatureCard>

<FeatureCard icon="🎚️" title="Configurable Size">

The default size for a player's first chest is set with <code>enderchest.default-size</code> in <code>config.yml</code>. Admins can also resize any individual chest with <code>/ee resize</code>.

- Valid sizes: <code>9</code>, <code>18</code>, <code>27</code>, <code>36</code>, <code>45</code>, <code>54</code>
- Invalid values are rounded to the nearest valid size
- Defaults to <code>54</code> (a full double chest)

</FeatureCard>

</CardGrid>

---

## 🗂️ Multi-Chest System

Players are no longer limited to one ender chest. Each player can own several, managed through an in-game menu.

<CardGrid>

<FeatureCard icon="📋" title="Chest List Menu">
Run <code>/eclist</code> to open a dialog listing every chest the player owns, each showing its slot count. An <strong>Edit mode</strong> checkbox switches what clicking a chest does: with it off (the default) a chest opens straight away; tick it and clicking a chest opens its management menu instead, where players can rename it or pick which chest is their main. The checkbox toggles in place, so flipping it never reopens the dialog.
</FeatureCard>

<FeatureCard icon="⭐" title="Main Chest">
With several chests, a player can pick one as their <strong>main</strong> — the one opened directly by <code>/ec</code> and by right-clicking an ender chest block. Until a main is chosen, those open the management menu instead. A new chest is never made main automatically; players set it from the menu (and can always reach the menu with <code>/eclist</code>).
</FeatureCard>

<FeatureCard icon="✏️" title="Custom Names">
Each chest can be given a custom name. Named chests show that name as their inventory title; unnamed chests fall back to <em>Ender Chest</em> (chest #1) or <em>Ender Chest {index}</em> for the rest.
</FeatureCard>

<FeatureCard icon="🛠️" title="Admin Management">
Admins can add, resize, and delete chests for any player with <code>/ee add</code>, <code>/ee resize</code>, and <code>/ee delete</code>. Deleting the main chest leaves the player with no main until they pick a new one from the menu.
</FeatureCard>

</CardGrid>

The chest menu is built on Paper's modern **Dialog API**, so navigation feels native and doesn't recenter the player's cursor as they move between screens.

---

## 💾 Database Storage

Every chest's contents are serialized and stored in a database — not in flat player files.

<CardGrid>

<FeatureCard icon="🗃️" title="Multiple Backends">

- **SQLite** — built in, zero setup, perfect for single servers
- **MySQL** / **MariaDB** — shared storage for networks
- **PostgreSQL** — for setups already running Postgres

</FeatureCard>

<FeatureCard icon="🚀" title="Async & Pooled">
All database work runs off the main thread on a dedicated executor, with a HikariCP connection pool. Saving never blocks the server tick.
</FeatureCard>

</CardGrid>

See the [Database](/docs/database) page for connection setup.

---

## 🛡️ No Item Duplication

EnhancedEchest is built so that ender chest contents can never be duplicated through reload exploits.

- Contents are **loaded fresh** from the database every time a chest is opened
- Contents are **saved immediately** when the chest is closed or the player quits
- A **pending-save chain** ensures the next open always waits for any in-flight save to finish before loading — so a player can never reopen and read stale data

---

## 🔄 Migration

Already have players with vanilla ender chest data? EnhancedEchest can import it.

- When <code>migration.enabled</code> is <code>true</code>, an un-migrated player's vanilla ender chest is imported automatically on join
- Admins can trigger migration manually for one player or everyone online with <code>/ee migrate run</code>
- Each player is migrated only once and flagged as done afterward

See the [Migration](/docs/migration) page for details.

---

## 🌿 Cross-Platform Support

EnhancedEchest uses a region-aware scheduler (FoliaLib) under the hood, so the same jar runs on:

| Platform | Supported |
|----------|-----------|
| **Paper** | ✅ |
| **Folia** | ✅ |
| **Purpur / Paper forks** | ✅ |

Built and tested against **Minecraft 26.1.x** on **Java 25** — other Minecraft versions are not supported.

All third-party libraries (database drivers, connection pool, scheduler) are shaded and relocated into the jar — **no extra downloads or server-side drivers required**.

---

## 🔔 Update Notifications

EnhancedEchest checks for new releases on startup and quietly notifies admins in-game when an update is available, with a clickable download link.

---

## 🌐 Localization

All player-facing text lives in editable language files. Ship a translation by copying the <code>en_US</code> folder, translating it, and pointing <code>language</code> at your new locale. Messages support full MiniMessage formatting. See the [Language](/docs/language) page.

# Features ✨

Here is an overview of everything **EnhancedEchest** brings to your Minecraft server.

<img class="showcase-shot" alt="Managing several ender chests in EnhancedEchest" src="/multiple-enderchests.gif" />

## 📦 Larger Ender Chests

EnhancedEchest replaces the vanilla 27-slot ender chest with a configurable GUI of up to **54 slots**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

<CardGrid>

<FeatureCard icon="🖱️" title="Same Block, More Space">

Players open their ender chest exactly the way they always have, by right-clicking an ender chest block, and get the larger inventory instead of the vanilla screen.

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

<img class="feature-shot" width="1422" height="1070" alt="Chest list menu" src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" />

<CardGrid>

<FeatureCard icon="📋" title="Chest List Menu">
Run <code>/eclist</code> to open a dialog listing every chest the player owns, each showing its slot count. An <strong>Edit mode</strong> checkbox switches what clicking a chest does: with it off (the default) a chest opens straight away; tick it and clicking a chest opens its management menu instead, where players can rename it, give it a custom icon, or pick which chest is their main. The checkbox toggles in place, so flipping it never reopens the dialog.
</FeatureCard>

<FeatureCard icon="⭐" title="Main Chest">
With several chests, a player can pick one as their <strong>main</strong>, the one opened directly by <code>/ec</code> and by right-clicking an ender chest block. Until a main is chosen, those open the management menu instead. A new chest is never made main automatically; players set it from the menu (and can always reach the menu with <code>/eclist</code>).
</FeatureCard>

<FeatureCard icon="🎨" title="Customize Each Chest">
Players personalize their chests straight from the in-game menu — no commands needed. Open a chest's management screen to:

- <strong>Rename it</strong> — a named chest shows that name as its inventory title (unnamed chests fall back to <em>Ender Chest</em> or <em>Ender Chest {index}</em>)
- <strong>Choose an icon</strong> — pick any item to represent the chest in the list, with a searchable item picker, or reset to the default ender-chest icon

</FeatureCard>

<FeatureCard icon="🛠️" title="Admin Management">
Admins can add, resize, and delete chests for any player with <code>/ee add</code>, <code>/ee resize</code>, and <code>/ee delete</code>. Deleting the main chest leaves the player with no main until they pick a new one from the menu.
</FeatureCard>

<FeatureCard icon="🎫" title="Permission-Granted Chests">
Hand out chests by rank instead of by command. The permission <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> grants that many chests at that size, e.g. <code>...2.slot.54</code> gives two 54-slot chests. Matching nodes <strong>stack</strong>, grants sync on open, and removing a node removes those chests (spilling any items to a recoverable temporary chest). The player's base chest is always protected. See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page.
</FeatureCard>

<FeatureCard icon="👁️" title="View Other Players' Chests">
With <code>/ee view &lt;player&gt;</code> an admin can open any player's chest, online or offline. One chest opens directly; with several, a <strong>picker menu</strong> lets you choose (or use <code>/ee view &lt;player&gt; list</code> to always show it, or <code>&lt;index&gt;</code> for a specific chest). Grant <code>admin.view</code> for a read-only look, or add <code>admin.edit</code> to take and add items. The admin joins the <strong>same live inventory</strong> the owner sees, so contents can never be duplicated (on Paper both can even edit at once).
</FeatureCard>

</CardGrid>

---

## 💾 Database Storage

Every chest's contents are serialized and stored in a database, not in flat player files.

<CardGrid>

<FeatureCard icon="🗃️" title="Multiple Backends">

- **SQLite**: built in, zero setup, perfect for single servers
- **MySQL** / **MariaDB**: shared storage for networks
- **PostgreSQL**: for setups already running Postgres

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
- A **pending-save chain** ensures the next open always waits for any in-flight save to finish before loading, so a player can never reopen and read stale data
- When two people view the same chest at once (e.g. an admin via `/ee view` and the owner), they share **one live inventory**, so even simultaneous editing can't dupe items (concurrent editing on Paper; one viewer at a time on Folia)

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

Built and tested against **Minecraft 26.1.x** on **Java 25**; other Minecraft versions are not supported.

All third-party libraries (database drivers, connection pool, scheduler) are shaded and relocated into the jar, so **no extra downloads or server-side drivers are required**.

---

## 🪨 Bedrock Support (Geyser)

EnhancedEchest's menus are built on Paper's modern **Dialog API**, and [Geyser](https://geysermc.org/)
automatically converts Java dialogs into native **Bedrock forms**. That means Bedrock players who join
your server through a Geyser proxy get the `/eclist` chest menu rendered as a proper Bedrock form,
buttons, inputs, and all, with **no extra configuration** on your side.

- The chest list, rename prompt, and "Set as main" actions all surface as Bedrock UI
- Nothing to install on the EnhancedEchest side: the conversion happens in Geyser
- The chest inventory itself is a normal container, so it works on Bedrock as usual

::: tip Geyser is doing the work
This support comes from Geyser's built-in Java-to-Bedrock form conversion, not from a Bedrock-specific
code path in the plugin. Keep your Geyser build reasonably up to date for the smoothest dialog
conversion.
:::

<!-- TODO(showcase): replace this box with a screenshot of the /eclist menu as a Bedrock form via Geyser. -->
<div class="showcase-placeholder">
  <span class="sp-icon">🪨</span>
  <span class="sp-title">Showcase: the menu as a Bedrock form</span>
  <span class="sp-hint">Drop a screenshot of a Bedrock client viewing the chest menu through Geyser here.</span>
</div>

---

## 🔔 Update Notifications

EnhancedEchest checks for new releases on startup and quietly notifies admins in-game when an update is available, with a clickable download link.

---

## 🌐 Localization

All player-facing text lives in editable language files. Ship a translation by copying the <code>en_US</code> folder, translating it, and pointing <code>language</code> at your new locale. Messages support full MiniMessage formatting. See the [Language](/docs/language) page.

---

## 📊 Usage Statistics

EnhancedEchest reports anonymous usage data to [bStats](https://bstats.org/plugin/bukkit/EnhancedEchest/32142),
so you can see how many servers run the plugin alongside breakdowns such as storage backend and language.
The collection is anonymous and can be turned off globally in `plugins/bStats/config.yml`.

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142" target="_blank" rel="noreferrer">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" style="max-width: 100%;">
  </a>
</p>

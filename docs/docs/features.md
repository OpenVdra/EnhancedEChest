# Features

Here is an overview of everything **EnhancedEchest** brings to your Minecraft server.

<CardGrid>

<DocCard icon="Package" title="Larger Ender Chests" link="#larger-ender-chests" desc="Up to 54 slots, configurable in multiples of nine." />

<DocCard icon="Archive" title="Multi-Chest System" link="#multi-chest-system" desc="Own several chests, each managed from an in-game menu." />

<DocCard icon="ArrowRightLeft" title="Migration" link="#migration" desc="Import players' existing vanilla ender chest data." />

<DocCard icon="Layers" title="Bedrock Support" link="#bedrock-support" desc="Menus render as native Bedrock forms via Geyser." />

<DocCard icon="Globe" title="Localization" link="#localization" desc="All player-facing text is editable and translatable." />

<DocCard icon="BarChart2" title="Usage Statistics" link="#usage-statistics" desc="Anonymous usage data reported to bStats." />

</CardGrid>

## Larger Ender Chests {#larger-ender-chests}

EnhancedEchest replaces the vanilla 27-slot ender chest with a configurable inventory of up to **54 slots**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

<CardGrid>

<FeatureCard icon="MousePointer2" title="Same Block, More Space">

Players open their ender chest the same way they always have, by right-clicking an ender chest block, and get the larger inventory instead of the vanilla screen.

- Opens on right-click or via <code>/ec</code>
- The ender chest block keeps its open/close lid animation
- Size is configurable in multiples of 9, from 9 up to 54

</FeatureCard>

<FeatureCard icon="Sliders" title="Configurable Size">

The default size for a player's first chest is set with <code>enderchest.default-size</code> in <code>config.yml</code>. Admins can also resize any individual chest with <code>/ee resize</code>.

- Valid sizes: <code>9</code>, <code>18</code>, <code>27</code>, <code>36</code>, <code>45</code>, <code>54</code>
- Invalid values are rounded to the nearest valid size
- Defaults to <code>54</code> (a full double chest)

</FeatureCard>

</CardGrid>

## Multi-Chest System {#multi-chest-system}

Players are no longer limited to one ender chest. Each player can own several, managed through an in-game menu.

<figure class="feature-figure">
  <img alt="The chest list menu showing several owned ender chests" src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" />
  <figcaption>With two or more chests, opening your ender chest brings up this menu of every chest you own, each with its slot count.</figcaption>
</figure>

<CardGrid>

<FeatureCard icon="List" title="Chest List Menu">
Run <code>/eclist</code> to open a menu listing every chest the player owns, each showing its slot count. An <strong>Edit mode</strong> checkbox switches what clicking a chest does: off by default, a chest opens straight away; tick it and clicking a chest opens its management screen where players can rename it, give it a custom icon, or set it as their main. The checkbox toggles in place without reopening the menu.
</FeatureCard>

<FeatureCard icon="Star" title="Main Chest">
With several chests, a player can pick one as their <strong>main</strong>, the one opened directly by <code>/ec</code> and by right-clicking an ender chest block. Until a main is chosen, those open the management menu instead. A new chest is never made main automatically; players set it from the menu (and can always reach the menu with <code>/eclist</code>).
</FeatureCard>

<FeatureCard icon="Palette" title="Customize Each Chest">
Players personalize their chests straight from the in-game menu, no commands needed. Open a chest's management screen to:

- <strong>Rename it</strong>: a named chest shows that name as its inventory title
- <strong>Choose an icon</strong>: pick any item to represent the chest in the list, with a searchable item picker, or reset to the default ender-chest icon

</FeatureCard>

<FeatureCard icon="Wrench" title="Admin Management">
Admins can add, resize, and delete chests for any player with <code>/ee add</code>, <code>/ee resize</code>, and <code>/ee delete</code>. Deleting a player's main chest leaves them with no main until they pick a new one from the menu.
</FeatureCard>

<FeatureCard icon="Key" title="Permission-Granted Chests">
Hand out chests by rank instead of by command. The permission <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> grants that many chests at that size. Multiple nodes stack, grants sync on open, and removing a node removes those chests (spilling any items to a recoverable temporary chest). The player's base chest is always kept. See the <a href="/docs/permissions#permission-granted-chests">Permissions</a> page.
</FeatureCard>

<FeatureCard icon="Eye" title="View Other Players' Chests">
With <code>/ee view &lt;player&gt;</code> an admin can open any player's chest, online or offline. One chest opens directly; with several, a picker menu lets you choose which one. Grant <code>admin.view</code> for a read-only look, or add <code>admin.edit</code> to take and add items.
</FeatureCard>

</CardGrid>

<div class="placeholder-row">
  <figure>
    <img width="1162" height="1067" alt="A chest's management menu with rename, icon, and set-as-main options" src="https://github.com/user-attachments/assets/76bc97fa-1dcb-4e39-8bde-9504ebc4d768" />
    <figcaption>A chest's management screen: rename it, choose an icon, or set it as your main.</figcaption>
  </figure>
  <figure>
    <img width="1013" height="1067" alt="The rename prompt for an ender chest" src="https://github.com/user-attachments/assets/573814dd-6f58-4e9c-b65a-58842e3ba2a2" />
    <figcaption>Renaming a chest; the name you enter becomes its inventory title.</figcaption>
  </figure>
</div>

<figure class="feature-figure">
  <img width="1802" height="1068" alt="The searchable item picker for choosing a chest icon" src="https://github.com/user-attachments/assets/ce6b235b-980c-4403-86d3-503c25f32d77" />
  <figcaption>Pick any item as the chest's icon with the searchable item picker.</figcaption>
</figure>

## Migration {#migration}

Already have players with ender chest data? EnhancedEchest can import it from vanilla ender chests, the AxVaults plugin, or the PlayerVaultsX plugin.

- When <code>migration.enabled</code> is <code>true</code>, an un-migrated player's vanilla ender chest is imported automatically on join
- Admins can trigger vanilla migration manually with <code>/ee migrate vanilla</code>
- Import from AxVaults with <code>/ee migrate axvaults</code>, including offline players (tested with AxVaults 2.15.0)
- Import from PlayerVaultsX with <code>/ee migrate playervaultsx</code>, including offline players (tested with PlayerVaultsX 4.4.13)

See the [Migration](/docs/migration) page for details.

## Bedrock Support {#bedrock-support}

Bedrock players who join through a [Geyser](https://geysermc.org/) proxy get the chest menu rendered as a proper Bedrock form, with no extra configuration needed on the EnhancedEchest side.

- The chest list, rename prompt, and "Set as main" actions all surface as Bedrock UI
- The chest inventory itself is a normal container and works on Bedrock as usual

::: tip
Keep your Geyser build reasonably up to date for the smoothest dialog conversion.
:::

## Localization {#localization}

All player-facing text lives in editable language files. Ship a translation by copying the <code>en_US</code> folder, translating it, and pointing <code>language</code> at your new locale. See the [Language](/docs/language) page.

## Usage Statistics {#usage-statistics}

EnhancedEchest reports anonymous usage data to [bStats](https://bstats.org/plugin/bukkit/EnhancedEchest/32142). The collection is anonymous and can be turned off globally in `plugins/bStats/config.yml`.

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142" target="_blank" rel="noreferrer">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" style="max-width: 100%;">
  </a>
</p>

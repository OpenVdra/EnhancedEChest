package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.ChestAdminCommand;
import com.enhancedechest.command.admin.MigrateAxVaultsCommand;
import com.enhancedechest.command.admin.MigratePlayerVaultsXCommand;
import com.enhancedechest.command.admin.ChestTransferCommand;
import com.enhancedechest.command.admin.MigrateVanillaCommand;
import com.enhancedechest.command.admin.ReloadCommand;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEchestBootstrap implements PluginBootstrap {

    /** Permission to open the ender chest GUI via command ({@code /enderchest}, {@code /eclist}). */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";
    // Admin subcommands each gate on their own permission node; there is no separate base-command
    // permission on the root literal (a player with any one node can run that subcommand).
    private static final String ADMIN_RELOAD_PERMISSION = "enhancedechest.admin.reload";
    private static final String ADMIN_MIGRATE_PERMISSION = "enhancedechest.admin.migrate";
    private static final String ADMIN_ADD_PERMISSION = "enhancedechest.admin.add";
    private static final String ADMIN_RESIZE_PERMISSION = "enhancedechest.admin.resize";
    private static final String ADMIN_DELETE_PERMISSION = "enhancedechest.admin.delete";
    private static final String ADMIN_TRANSFER_PERMISSION = "enhancedechest.admin.transfer";
    // /ee view requires this; modifying (take/add) further requires enhancedechest.admin.edit,
    // checked per-click in EnderChestGuiListener so a view-only admin can look but not touch.
    private static final String ADMIN_VIEW_PERMISSION = "enhancedechest.admin.view";

    // Suggestion tooltips and value tables are precomputed once: suggestion providers run on every
    // keystroke, so building Messages/arrays inside them would allocate on the command's hot path.
    // Each suggestion carries a tooltip naming what the value is (shown beside the entry), and the
    // word-based arguments add an info header (see suggestHeader) above their values.
    private static final Message PLAYER_TOOLTIP = new LiteralMessage("Player");
    private static final Message OFFLINE_PLAYER_TOOLTIP = new LiteralMessage("Player (offline)");
    private static final Message HEADER_TOOLTIP = new LiteralMessage("Info only — not a value");

    /** Cap on how many player names a suggestion lists, so a huge offline roster can't flood the client. */
    private static final int MAX_PLAYER_SUGGESTIONS = 50;

    private static final int[] SIZE_VALUES = {9, 18, 27, 36, 45, 54};
    private static final Message[] SIZE_TOOLTIPS = sizeTooltips();

    private static final int[] COUNT_VALUES = {1, 2, 3, 5, 10};
    private static final Message[] COUNT_TOOLTIPS = countTooltips();

    private static final String[] DURATION_VALUES = {"1h", "12h", "1d", "7d", "30d"};
    private static final Message[] DURATION_TOOLTIPS = {
            new LiteralMessage("Duration — 1 hour"),
            new LiteralMessage("Duration — 12 hours"),
            new LiteralMessage("Duration — 1 day"),
            new LiteralMessage("Duration — 7 days"),
            new LiteralMessage("Duration — 30 days"),
    };

    private static Message[] sizeTooltips() {
        Message[] tips = new Message[SIZE_VALUES.length];
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            tips[i] = new LiteralMessage("Chest size — " + SIZE_VALUES[i] + " slots");
        }
        return tips;
    }

    private static Message[] countTooltips() {
        Message[] tips = new Message[COUNT_VALUES.length];
        for (int i = 0; i < COUNT_VALUES.length; i++) {
            tips[i] = new LiteralMessage(COUNT_VALUES[i] == 1 ? "1 chest" : COUNT_VALUES[i] + " chests");
        }
        return tips;
    }

    /** Suggests names of currently online players for the <player> argument (online-only commands). */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        suggestHeader(builder, "(player)");
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(name, PLAYER_TOOLTIP);
            }
        }
        return builder.buildFuture();
    };

    /**
     * Suggests known players for the <player> argument of commands that accept <b>offline</b> targets
     * (add / resize / delete / view). Online players are listed first; once at least one character has
     * been typed, offline players who have joined before are appended (filtered by the typed prefix and
     * capped at {@link #MAX_PLAYER_SUGGESTIONS}, so a large roster can't flood the client). The empty
     * state shows only online names, keeping it tidy — type to search the offline roster.
     */
    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, builder) -> {
        suggestHeader(builder, "(player)");
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        int added = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String name = p.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix) && seen.add(name.toLowerCase(Locale.ROOT))) {
                builder.suggest(name, PLAYER_TOOLTIP);
                if (++added >= MAX_PLAYER_SUGGESTIONS) return builder.buildFuture();
            }
        }
        // Scan the offline roster only once the admin starts typing, to avoid a full scan (and a noisy
        // dropdown) on the empty state.
        if (!prefix.isEmpty()) {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                String name = p.getName();
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith(prefix) && seen.add(lower)) {
                    builder.suggest(name, OFFLINE_PLAYER_TOOLTIP);
                    if (++added >= MAX_PLAYER_SUGGESTIONS) break;
                }
            }
        }
        return builder.buildFuture();
    };

    /**
     * Resolves a player name to a UUID from already-cached data only — online players, then the offline
     * roster (`getOfflinePlayers`) — <b>without</b> the blocking {@code getOfflinePlayer(String)} web
     * lookup, so it is safe to call on the suggestion hot path. Returns null if no cached player matches.
     */
    private static UUID knownPlayerUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) return p.getUniqueId();
        }
        return null;
    }

    /**
     * Suggests the valid chest sizes (multiples of 9, from 9 to 54) for the {@code <size>} argument.
     * No info header here: this is an integer argument, and Minecraft groups numeric suggestions above
     * text ones, so a text header is always forced to the bottom (the per-value tooltip names it
     * instead). Only the word-based arguments (player, duration) can show a header on top.
     */
    private static final SuggestionProvider<CommandSourceStack> CHEST_SIZES = (ctx, builder) -> {
        for (int i = 0; i < SIZE_VALUES.length; i++) {
            builder.suggest(SIZE_VALUES[i], SIZE_TOOLTIPS[i]);
        }
        return builder.buildFuture();
    };

    /** Suggests common chest counts for the optional {@code <count>} argument of /ee add. (Integer
     *  argument — see {@link #CHEST_SIZES} for why it carries no info header.) */
    private static final SuggestionProvider<CommandSourceStack> CHEST_COUNTS = (ctx, builder) -> {
        for (int i = 0; i < COUNT_VALUES.length; i++) {
            builder.suggest(COUNT_VALUES[i], COUNT_TOOLTIPS[i]);
        }
        return builder.buildFuture();
    };

    /** Suggests a few common durations for the optional {@code <duration>} argument of /ee add. */
    private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, builder) -> {
        suggestHeader(builder, "(duration)");
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (int i = 0; i < DURATION_VALUES.length; i++) {
            if (DURATION_VALUES[i].startsWith(prefix)) {
                builder.suggest(DURATION_VALUES[i], DURATION_TOOLTIPS[i]);
            }
        }
        return builder.buildFuture();
    };

    /**
     * Adds a purely-informational header above the real suggestions (e.g. {@code (player)}) so the
     * user can see what the argument expects without hovering.
     *
     * <p>Brigadier sorts suggestions by text, so the label must begin with a character that sorts
     * <i>before</i> the values to land first. {@code (} (ASCII 40) sorts before digits and letters, so
     * {@code (label)} pins it to the top cleanly with no leading space. This only works for word-based
     * arguments: on integer arguments Minecraft groups numeric suggestions above text ones, so a text
     * header is forced to the bottom — those arguments rely on their per-value tooltips instead.
     *
     * <p>The header is only shown while nothing has been typed yet — once the player starts typing a
     * value it disappears. Completing it produces an invalid value (it is a hint, not a real option).
     */
    private static void suggestHeader(SuggestionsBuilder builder, String label) {
        if (builder.getRemaining().isEmpty()) {
            builder.suggest(label, HEADER_TOOLTIP);
        }
    }

    /** Suggests the sender's own chests as {@code #index} and custom-name completions for /ec. */
    private static final SuggestionProvider<CommandSourceStack> OWN_CHESTS = (ctx, builder) -> {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        String prefix = builder.getRemaining().toLowerCase();
        return plugin.getStorageGateway().listChestsAsync(player.getUniqueId())
                .thenApply(chests -> {
                    for (var chest : chests) {
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        String idx = "#" + chest.index();
                        if (idx.toLowerCase().startsWith(prefix)) {
                            builder.suggest(idx, new LiteralMessage(
                                    named ? name : "Ender chest " + chest.index()));
                        }
                        if (named && name.toLowerCase().startsWith(prefix)) {
                            builder.suggest(name, new LiteralMessage("Ender chest " + chest.index()));
                        }
                    }
                    return builder.build();
                });
    };

    /**
     * Suggests the target player's chests as {@code <index>} completions for {@code /ee view <player>}.
     * Reads the already-typed {@code player} argument and resolves it from cached data (online or the
     * offline roster), so the index list also works for offline owners.
     */
    private static final SuggestionProvider<CommandSourceStack> TARGET_CHESTS = (ctx, builder) -> {
        String playerName;
        try {
            playerName = StringArgumentType.getString(ctx, "player");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        UUID target = knownPlayerUuid(playerName);
        if (target == null) {
            return builder.buildFuture();
        }
        return plugin.getStorageGateway().listChestsAsync(target)
                .thenApply(chests -> {
                    for (var chest : chests) {
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        builder.suggest(chest.index(), new LiteralMessage(
                                named ? name : "Ender chest " + chest.index()));
                    }
                    return builder.build();
                });
    };

    /**
     * Suggests transfer targets for {@code /ee transfer <from> <to> <target>}: the literal {@code all},
     * plus the <i>source</i> player's chests as {@code #index} and custom-name completions. The source is
     * read from the already-typed {@code from} argument and resolved from cached data (online or the
     * offline roster), so it also works for offline source players. {@code target} is a greedy string, so
     * suggestions are filtered by the typed prefix.
     */
    private static final SuggestionProvider<CommandSourceStack> TRANSFER_TARGETS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        if ("all".startsWith(prefix)) {
            builder.suggest("all", new LiteralMessage("Every chest (full account transfer)"));
        }
        String fromName;
        try {
            fromName = StringArgumentType.getString(ctx, "from");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        UUID source = knownPlayerUuid(fromName);
        if (source == null) {
            return builder.buildFuture();
        }
        return plugin.getStorageGateway().listChestsAsync(source)
                .thenApply(chests -> {
                    for (var chest : chests) {
                        if (chest.kind() == com.enhancedechest.model.ChestKind.TEMP) continue;
                        String name = chest.customName();
                        boolean named = name != null && !name.isBlank();
                        String idx = "#" + chest.index();
                        if (idx.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(idx, new LiteralMessage(named ? name : "Ender chest " + chest.index()));
                        }
                        if (named && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            builder.suggest(name, new LiteralMessage("Ender chest " + chest.index()));
                        }
                    }
                    return builder.build();
                });
    };

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            registerPlayerCommands(commands);
            registerAdminCommands(commands);
        });
    }

    private void registerPlayerCommands(Commands commands) {
        commands.register(
                Commands.literal("enderchest")
                        .requires(src -> src.getSender().hasPermission(OPEN_GUI_PERMISSION))
                        .executes(ctx -> EnderChestOpenCommand.execute(ctx.getSource()))
                        // /enderchest <#index | name> — open a specific chest by index or custom name
                        .then(Commands.argument("chest", StringArgumentType.greedyString())
                                .suggests(OWN_CHESTS)
                                .executes(ctx -> EnderChestOpenCommand.executeOpenTarget(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "chest"))))
                        .build(),
                "Open your enhanced enderchest",
                List.of("ec")
        );

        // /eclist — open the chest management menu
        commands.register(
                Commands.literal("eclist")
                        .requires(src -> src.getSender().hasPermission(OPEN_GUI_PERMISSION))
                        .executes(ctx -> EnderChestOpenCommand.executeList(ctx.getSource()))
                        .build(),
                "Open your enhanced enderchest management menu"
        );
    }

    private void registerAdminCommands(Commands commands) {
        commands.register(
                Commands.literal("enhancedechest")
                        .then(Commands.literal("migrate")
                                // /ee migrate vanilla [all|<player>] — import vanilla ender chests
                                .then(Commands.literal("vanilla")
                                        .requires(src -> src.getSender().hasPermission(ADMIN_MIGRATE_PERMISSION))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateVanillaCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(ONLINE_PLAYERS)
                                                .executes(ctx -> MigrateVanillaCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player")))))
                                // /ee migrate axvaults [all|<player>] — import vaults from AxVaults
                                .then(Commands.literal("axvaults")
                                        .requires(src -> src.getSender().hasPermission(ADMIN_MIGRATE_PERMISSION))
                                        .executes(ctx -> MigrateAxVaultsCommand.executeAll(ctx.getSource()))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateAxVaultsCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .executes(ctx -> MigrateAxVaultsCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player")))))
                                // /ee migrate playervaultsx [all|<player>] — import vaults from PlayerVaultsX
                                .then(Commands.literal("playervaultsx")
                                        .requires(src -> src.getSender().hasPermission(ADMIN_MIGRATE_PERMISSION))
                                        .executes(ctx -> MigratePlayerVaultsXCommand.executeAll(ctx.getSource()))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigratePlayerVaultsXCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .executes(ctx -> MigratePlayerVaultsXCommand.executePlayer(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission(ADMIN_RELOAD_PERMISSION))
                                .executes(ctx -> ReloadCommand.execute(ctx.getSource())))
                        // /ee add <player> <size> [count] [duration] — a single linear chain so each
                        // node has one argument child (two sibling argument children break Brigadier's
                        // suggestions, since word() matches the empty trailing token).
                        .then(Commands.literal("add")
                                .requires(src -> src.getSender().hasPermission(ADMIN_ADD_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                .suggests(CHEST_SIZES)
                                                .executes(ctx -> ChestAdminCommand.add(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "size")))
                                                // Optional count → create several chests at once (default 1).
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                                        .suggests(CHEST_COUNTS)
                                                        .executes(ctx -> ChestAdminCommand.add(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))
                                                        // Optional duration → expiring chests (e.g. 7d, 1h, 1d_12h).
                                                        .then(Commands.argument("duration", StringArgumentType.word())
                                                                .suggests(DURATIONS)
                                                                .executes(ctx -> ChestAdminCommand.add(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "player"),
                                                                        IntegerArgumentType.getInteger(ctx, "size"),
                                                                        IntegerArgumentType.getInteger(ctx, "count"),
                                                                        StringArgumentType.getString(ctx, "duration"))))))))
                        // /ee view <player> [list | index] — open another player's chest, sharing the
                        // live session (concurrent edit on Paper, single-viewer on Folia). No argument:
                        // 1 chest opens directly, 2+ show the picker dialog. The literal 'list' forces
                        // the picker even for a single chest. Modifying requires enhancedechest.admin.edit.
                        .then(Commands.literal("view")
                                .requires(src -> src.getSender().hasPermission(ADMIN_VIEW_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .executes(ctx -> ChestAdminCommand.view(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player")))
                                        // Literal 'list' → always open the chest picker for the target.
                                        .then(Commands.literal("list")
                                                .executes(ctx -> ChestAdminCommand.viewList(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"))))
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .suggests(TARGET_CHESTS)
                                                .executes(ctx -> ChestAdminCommand.view(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "index"))))))
                        // /ee resize <player> <index> <size>
                        .then(Commands.literal("resize")
                                .requires(src -> src.getSender().hasPermission(ADMIN_RESIZE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("size", IntegerArgumentType.integer(9, 54))
                                                        .suggests(CHEST_SIZES)
                                                        .executes(ctx -> ChestAdminCommand.resize(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "index"),
                                                                IntegerArgumentType.getInteger(ctx, "size")))))))
                        // /ee delete <player> <count> [force] — delete the <count> newest chests
                        // (highest indices); the player's first chest is always kept.
                        .then(Commands.literal("delete")
                                .requires(src -> src.getSender().hasPermission(ADMIN_DELETE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .suggests(CHEST_COUNTS)
                                                .executes(ctx -> ChestAdminCommand.delete(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))
                                                // Literal 'force' → hard-delete (items lost); default spills to a temp chest.
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> ChestAdminCommand.deleteForce(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "count")))))))
                        // /ee transfer <from> <to> <index|name|all> [override|temp] — move one player's
                        // chests onto another account (account switch). The target and the optional
                        // override/temp flag share one greedy argument so the target may be a #index, a
                        // custom name (which can contain spaces), or 'all'; the flag is parsed off the end.
                        .then(Commands.literal("transfer")
                                .requires(src -> src.getSender().hasPermission(ADMIN_TRANSFER_PERMISSION))
                                .then(Commands.argument("from", StringArgumentType.word())
                                        .suggests(KNOWN_PLAYERS)
                                        .then(Commands.argument("to", StringArgumentType.word())
                                                .suggests(KNOWN_PLAYERS)
                                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                                        .suggests(TRANSFER_TARGETS)
                                                        .executes(ctx -> ChestTransferCommand.transfer(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "from"),
                                                                StringArgumentType.getString(ctx, "to"),
                                                                StringArgumentType.getString(ctx, "target")))))))
                        .build(),
                "EnhancedEchest admin commands",
                List.of("ee")
        );
    }
}

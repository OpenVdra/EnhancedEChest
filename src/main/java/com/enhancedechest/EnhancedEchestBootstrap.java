package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.ChestAdminCommand;
import com.enhancedechest.command.admin.MigrateRunCommand;
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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEchestBootstrap implements PluginBootstrap {

    /** Permission to open the ender chest GUI via command ({@code /enderchest}, {@code /eclist}). */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";
    // Admin commands require the base node AND the command-specific node. Brigadier enforces both:
    // the root literal checks the base, each subcommand checks its own node.
    private static final String ADMIN_BASE_PERMISSION = "enhancedechest.admin";
    private static final String ADMIN_RELOAD_PERMISSION = "enhancedechest.admin.reload";
    private static final String ADMIN_MIGRATE_PERMISSION = "enhancedechest.admin.migrate.run";
    private static final String ADMIN_ADD_PERMISSION = "enhancedechest.admin.add";
    private static final String ADMIN_RESIZE_PERMISSION = "enhancedechest.admin.resize";
    private static final String ADMIN_DELETE_PERMISSION = "enhancedechest.admin.delete";

    // Suggestion tooltips and value tables are precomputed once: suggestion providers run on every
    // keystroke, so building Messages/arrays inside them would allocate on the command's hot path.
    // Each suggestion carries a tooltip naming what the value is (shown beside the entry), and the
    // word-based arguments add an info header (see suggestHeader) above their values.
    private static final Message PLAYER_TOOLTIP = new LiteralMessage("Player");
    private static final Message HEADER_TOOLTIP = new LiteralMessage("Info only — not a value");

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

    /** Suggests names of currently online players for the <player> argument. */
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
        return plugin.getEnderChestService().listChestsAsync(player.getUniqueId())
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
                        .requires(src -> src.getSender().hasPermission(ADMIN_BASE_PERMISSION))
                        .then(Commands.literal("migrate")
                                .then(Commands.literal("run")
                                        .requires(src -> src.getSender().hasPermission(ADMIN_MIGRATE_PERMISSION))
                                        .then(Commands.literal("all")
                                                .executes(ctx -> MigrateRunCommand.executeAll(ctx.getSource())))
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(ONLINE_PLAYERS)
                                                .executes(ctx -> MigrateRunCommand.executePlayer(
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
                                        .suggests(ONLINE_PLAYERS)
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
                        // /ee resize <player> <index> <size>
                        .then(Commands.literal("resize")
                                .requires(src -> src.getSender().hasPermission(ADMIN_RESIZE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
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
                                        .suggests(ONLINE_PLAYERS)
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
                        .build(),
                "EnhancedEchest admin commands",
                List.of("ee")
        );
    }
}

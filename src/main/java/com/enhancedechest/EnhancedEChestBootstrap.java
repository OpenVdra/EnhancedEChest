package com.enhancedechest;

import com.enhancedechest.command.EnderChestOpenCommand;
import com.enhancedechest.command.admin.ChestAdminCommand;
import com.enhancedechest.command.admin.MigrateRunCommand;
import com.enhancedechest.command.admin.ReloadCommand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EnhancedEChestBootstrap implements PluginBootstrap {

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

    /** Suggests names of currently online players for the <player> argument. */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (ctx, builder) -> {
        String prefix = builder.getRemaining().toLowerCase();
        Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getName())
                .filter(name -> name.toLowerCase().startsWith(prefix))
                .forEach(builder::suggest);
        return builder.buildFuture();
    };

    /** Suggests the valid chest sizes (multiples of 9, from 9 to 54) for the {@code <size>} argument. */
    private static final SuggestionProvider<CommandSourceStack> CHEST_SIZES = (ctx, builder) -> {
        for (int size = 9; size <= 54; size += 9) {
            builder.suggest(size);
        }
        return builder.buildFuture();
    };

    /** Suggests a few common durations for the optional {@code <duration>} argument of /ee add. */
    private static final SuggestionProvider<CommandSourceStack> DURATIONS = (ctx, builder) -> {
        for (String d : new String[]{"1h", "12h", "1d", "7d", "30d"}) {
            if (d.startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(d);
            }
        }
        return builder.buildFuture();
    };

    /** Suggests the sender's own chests as {@code #index} and custom-name completions for /ec. */
    private static final SuggestionProvider<CommandSourceStack> OWN_CHESTS = (ctx, builder) -> {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            return builder.buildFuture();
        }
        EnhancedEChestPlugin plugin =
                (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            return builder.buildFuture();
        }
        String prefix = builder.getRemaining().toLowerCase();
        return plugin.getEnderChestService().listChestsAsync(player.getUniqueId())
                .thenApply(chests -> {
                    for (var chest : chests) {
                        String idx = "#" + chest.index();
                        if (idx.toLowerCase().startsWith(prefix)) {
                            builder.suggest(idx);
                        }
                        String name = chest.customName();
                        if (name != null && !name.isBlank()
                                && name.toLowerCase().startsWith(prefix)) {
                            builder.suggest(name);
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
                        // /ee add <player> <size> [duration]
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
                                                // Optional duration → an expiring chest (e.g. 7d, 1h, 1d_12h).
                                                .then(Commands.argument("duration", StringArgumentType.word())
                                                        .suggests(DURATIONS)
                                                        .executes(ctx -> ChestAdminCommand.add(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                                StringArgumentType.getString(ctx, "duration")))))))
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
                        // /ee delete <player> <index> [force]
                        .then(Commands.literal("delete")
                                .requires(src -> src.getSender().hasPermission(ADMIN_DELETE_PERMISSION))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(ONLINE_PLAYERS)
                                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                                .executes(ctx -> ChestAdminCommand.delete(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "index")))
                                                // Literal 'force' → hard-delete (items lost); default spills to a temp chest.
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> ChestAdminCommand.deleteForce(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                IntegerArgumentType.getInteger(ctx, "index")))))))
                        .build(),
                "EnhancedEChest admin commands",
                List.of("ee")
        );
    }
}

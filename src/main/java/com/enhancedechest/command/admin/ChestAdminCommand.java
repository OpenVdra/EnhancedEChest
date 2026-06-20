package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEChestPlugin;
import com.enhancedechest.config.PluginConfig;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.util.DurationFormat;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Admin chest management: /ee add|resize|delete &lt;player&gt; [index] [size] [duration|force].
 *
 * Adding allocates the next free index (no index argument) and may grant an expiring chest.
 * Resizing spills cut-off items into a temp chest; deleting spills by default, or hard-deletes
 * with the literal {@code force}. All DB work runs async on the service executor (which serializes
 * item-moving operations and force-closes open GUIs); the result is reported back to the sender.
 */
public final class ChestAdminCommand {

    private ChestAdminCommand() {}

    public static int add(CommandSourceStack source, String playerName, int size) {
        return doAdd(source, playerName, size, null);
    }

    public static int add(CommandSourceStack source, String playerName, int size, String duration) {
        return doAdd(source, playerName, size, duration);
    }

    private static int doAdd(CommandSourceStack source, String playerName, int size, @Nullable String duration) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        if (!PluginConfig.isValidSize(size)) {
            ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
            return 0;
        }

        Long expiresAt = null;
        String durationLabel = null;
        if (duration != null) {
            long durationMillis;
            try {
                durationMillis = DurationFormat.parse(duration);
            } catch (IllegalArgumentException e) {
                ctx.sender.sendMessage(ctx.lang.get("admin.invalid-duration", "duration", duration));
                return 0;
            }
            expiresAt = System.currentTimeMillis() + durationMillis;
            durationLabel = DurationFormat.formatRemaining(durationMillis);
        }

        final String label = durationLabel;
        ctx.service.createChestAsync(ctx.target, size, expiresAt).thenAccept(index -> {
            if (label == null) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-added",
                        "player", playerName,
                        "index", Integer.toString(index),
                        "size", Integer.toString(size)));
            } else {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-added-expiring",
                        "player", playerName,
                        "index", Integer.toString(index),
                        "size", Integer.toString(size),
                        "duration", label));
            }
        });
        return 1;
    }

    public static int resize(CommandSourceStack source, String playerName, int index, int size) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        if (!PluginConfig.isValidSize(size)) {
            ctx.sender.sendMessage(ctx.lang.get("admin.invalid-size"));
            return 0;
        }

        ctx.service.listChestsAsync(ctx.target).thenAccept(chests -> {
            if (chests.stream().noneMatch(c -> c.index() == index)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                        "player", playerName, "index", Integer.toString(index)));
                return;
            }
            // Routes through the spill-aware path: a shrink below used slots overflows to a temp chest.
            ctx.service.resizeOrSpill(ctx.target, index, size).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get("admin.chest-resized",
                            "player", playerName,
                            "index", Integer.toString(index),
                            "size", Integer.toString(size))));
        });
        return 1;
    }

    public static int delete(CommandSourceStack source, String playerName, int index) {
        return doDelete(source, playerName, index, false);
    }

    public static int deleteForce(CommandSourceStack source, String playerName, int index) {
        return doDelete(source, playerName, index, true);
    }

    private static int doDelete(CommandSourceStack source, String playerName, int index, boolean force) {
        Ctx ctx = resolve(source, playerName);
        if (ctx == null) return 0;

        ctx.service.listChestsAsync(ctx.target).thenAccept(chests -> {
            if (chests.stream().noneMatch(c -> c.index() == index)) {
                ctx.sender.sendMessage(ctx.lang.get("admin.chest-not-found",
                        "player", playerName, "index", Integer.toString(index)));
                return;
            }
            ctx.service.removeChest(ctx.target, index, force).thenRun(() ->
                    ctx.sender.sendMessage(ctx.lang.get(
                            force ? "admin.chest-deleted" : "admin.chest-deleted-spilled",
                            "player", playerName, "index", Integer.toString(index))));
        });
        return 1;
    }

    // ---- helpers ----

    private record Ctx(CommandSender sender, EnderChestService service, LanguageManager lang, UUID target) {}

    private static Ctx resolve(CommandSourceStack source, String playerName) {
        CommandSender sender = source.getSender();
        EnhancedEChestPlugin plugin =
                (EnhancedEChestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEChest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEChest] Plugin is not available."));
            return null;
        }
        LanguageManager lang = plugin.getLanguageManager();

        UUID target = resolveUuid(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("admin.player-not-found", "player", playerName));
            return null;
        }
        return new Ctx(sender, plugin.getEnderChestService(), lang, target);
    }

    @SuppressWarnings("deprecation")
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }
}

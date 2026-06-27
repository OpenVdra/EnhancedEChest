package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.service.ChestTransferService.ConflictPolicy;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * {@code /ee transfer <from> <to> <index|name|all> [override|temp]} — moves a player's ender chests onto
 * another account when someone switches accounts. The conflict flag and the target are parsed out of a
 * single greedy argument so the target may be {@code all}, a {@code #index} or a custom chest name (which
 * can contain spaces), with the optional {@code override}/{@code temp} flag as the last word.
 */
public final class ChestTransferCommand {

    private ChestTransferCommand() {}

    public static int transfer(CommandSourceStack source, String fromName, String toName, String rest) {
        CommandSender sender = source.getSender();
        EnhancedEchestPlugin plugin =
                (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            sender.sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }
        LanguageManager lang = plugin.getLanguageManager();

        // Split a trailing override/temp flag off the greedy "rest" argument; the remainder is the target.
        String trimmed = rest.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        ConflictPolicy policy = ConflictPolicy.ASK;
        String target = trimmed;
        if (lower.endsWith(" override")) {
            policy = ConflictPolicy.OVERRIDE;
            target = trimmed.substring(0, trimmed.length() - " override".length()).trim();
        } else if (lower.endsWith(" temp")) {
            policy = ConflictPolicy.TEMP;
            target = trimmed.substring(0, trimmed.length() - " temp".length()).trim();
        }
        if (target.isEmpty()) {
            sender.sendMessage(lang.get("admin.transfer-usage"));
            return 0;
        }

        UUID from = resolveUuid(fromName);
        if (from == null) {
            sender.sendMessage(lang.get("admin.player-not-found", "player", fromName));
            return 0;
        }
        UUID to = resolveUuid(toName);
        if (to == null) {
            sender.sendMessage(lang.get("admin.player-not-found", "player", toName));
            return 0;
        }

        plugin.getChestTransferService().transfer(sender, fromName, from, toName, to, target, policy);
        return 1;
    }

    @SuppressWarnings("deprecation")
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline.getUniqueId() : null;
    }
}

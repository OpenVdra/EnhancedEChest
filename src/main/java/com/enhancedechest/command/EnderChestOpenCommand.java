package com.enhancedechest.command;

import com.enhancedechest.EnhancedEchestPlugin;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class EnderChestOpenCommand {

    private EnderChestOpenCommand() {}

    public static int execute(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }

        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(plugin.getLanguageManager().get("command.not-player"));
            return 0;
        }

        plugin.getChestOpener().open(player, null);
        return Command.SINGLE_SUCCESS;
    }

    /** /eclist — opens the management dialog listing all of the player's chests. */
    public static int executeList(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }

        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(plugin.getLanguageManager().get("command.not-player"));
            return 0;
        }

        plugin.getChestOpener().openListDialog(player);
        return Command.SINGLE_SUCCESS;
    }

    /** /ec &lt;#index | name&gt; — opens a specific chest by index or custom name. */
    public static int executeOpenTarget(CommandSourceStack source, String target) {
        EnhancedEchestPlugin plugin = (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return 0;
        }

        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(plugin.getLanguageManager().get("command.not-player"));
            return 0;
        }

        plugin.getChestOpener().openByQuery(player, target);
        return Command.SINGLE_SUCCESS;
    }
}

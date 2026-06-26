package com.enhancedechest.command.admin;

import com.enhancedechest.EnhancedEchestPlugin;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.migration.PlayerVaultsXMigrationService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * {@code /enhancedechest migrate playervaultsx [all|<player>]} — imports vaults from a PlayerVaultsX
 * installation into EnhancedEchest. The heavy work (file read, item decode, storage writes) runs on
 * the shared DB executor so the server thread is never blocked; the result is reported when it
 * finishes.
 */
public final class MigratePlayerVaultsXCommand {

    /** Display name substituted into the shared {@code migrate.*} messages as {@code {source}}. */
    private static final String SOURCE = "PlayerVaultsX";

    private MigratePlayerVaultsXCommand() {}

    public static int executeAll(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        LanguageManager lang = plugin.getLanguageManager();
        PlayerVaultsXMigrationService service = plugin.getPlayerVaultsXMigrationService();
        CommandSender sender = source.getSender();

        if (!service.sourceAvailable()) {
            sender.sendMessage(lang.get("migrate.no-source", "source", SOURCE));
            return 0;
        }

        sender.sendMessage(lang.get("migrate.started", "source", SOURCE));
        plugin.getDbExecutor().supply(() -> {
            try {
                return service.migrateAll();
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("[PlayerVaultsX] Migration failed", e);
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> {
            if (error != null) {
                sender.sendMessage(lang.get("migrate.import-failed", "source", SOURCE, "error", rootMessage(error)));
                return;
            }
            sender.sendMessage(lang.get("migrate.import-done",
                    "source", SOURCE,
                    "players", String.valueOf(result.playersMigrated()),
                    "vaults", String.valueOf(result.vaultsMigrated()),
                    "skipped", String.valueOf(result.vaultsSkipped()),
                    "failed", String.valueOf(result.playersFailed())));
        });
        return 1;
    }

    public static int executePlayer(CommandSourceStack source, String playerName) {
        EnhancedEchestPlugin plugin = resolve(source);
        if (plugin == null) return 0;

        LanguageManager lang = plugin.getLanguageManager();
        PlayerVaultsXMigrationService service = plugin.getPlayerVaultsXMigrationService();
        CommandSender sender = source.getSender();

        if (!service.sourceAvailable()) {
            sender.sendMessage(lang.get("migrate.no-source", "source", SOURCE));
            return 0;
        }

        UUID target = resolveUuid(playerName);
        if (target == null) {
            sender.sendMessage(lang.get("admin.player-not-found", "player", playerName));
            return 0;
        }

        sender.sendMessage(lang.get("migrate.started", "source", SOURCE));
        plugin.getDbExecutor().supply(() -> {
            try {
                return service.migratePlayer(target);
            } catch (Exception e) {
                plugin.getSLF4JLogger().error("[PlayerVaultsX] Migration failed for {}", playerName, e);
                throw new RuntimeException(e);
            }
        }).whenComplete((result, error) -> {
            if (error != null) {
                sender.sendMessage(lang.get("migrate.import-failed", "source", SOURCE, "error", rootMessage(error)));
                return;
            }
            sender.sendMessage(lang.get("migrate.import-player-done",
                    "source", SOURCE,
                    "player", playerName,
                    "vaults", String.valueOf(result.vaultsMigrated()),
                    "skipped", String.valueOf(result.vaultsSkipped())));
        });
        return 1;
    }

    /** Resolves a name to a UUID, preferring an online player, then Bukkit's offline-player cache. */
    private static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null ? cached.getUniqueId() : null;
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg != null ? msg : cause.getClass().getSimpleName();
    }

    private static EnhancedEchestPlugin resolve(CommandSourceStack source) {
        EnhancedEchestPlugin plugin = (EnhancedEchestPlugin) Bukkit.getPluginManager().getPlugin("EnhancedEchest");
        if (plugin == null || !plugin.isEnabled()) {
            source.getSender().sendMessage(Component.text("[EnhancedEchest] Plugin is not available."));
            return null;
        }
        return plugin;
    }
}

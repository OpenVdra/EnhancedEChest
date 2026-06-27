package com.enhancedechest.update;

import com.enhancedechest.lang.LanguageManager;
import com.tcoded.folialib.FoliaLib;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class UpdateNotifyListener implements Listener {

    private final FoliaLib foliaLib;
    private final UpdateChecker checker;
    private final LanguageManager lang;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!checker.isUpdateAvailable()) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        // Slight delay so the player is fully loaded before receiving messages.
        // runAtEntityLater targets the player's entity thread (safe for Folia),
        // 2 seconds ≈ 40 ticks at 20 TPS.
        foliaLib.getScheduler().runAtEntityLater(player, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(lang.get("update.available",
                    "current", checker.getCurrentVersion(),
                    "latest",  checker.getLatestVersion()));
            player.sendMessage(lang.get("update.download",
                    "url", checker.getDownloadUrl()));
        }, 2L, TimeUnit.SECONDS);
    }
}

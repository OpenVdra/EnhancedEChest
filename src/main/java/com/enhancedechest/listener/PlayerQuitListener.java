package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.service.ChestSessionManager;
import com.tcoded.folialib.FoliaLib;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * Backstop detach when the player disconnects while the custom GUI is open.
 *
 * In most cases, Paper fires InventoryCloseEvent before PlayerQuitEvent, so
 * EnderChestGuiListener already detached this viewer. This listener is a safety net for
 * edge cases (e.g., server-side forced disconnects) where close may not have fired.
 * detach is idempotent — once a viewer is removed (or the session already persisted),
 * a second call is a no-op, so calling it from both listeners is harmless.
 */
@RequiredArgsConstructor
public final class PlayerQuitListener implements Listener {

    private final ChestSessionManager sessions;
    @SuppressWarnings("unused") // retained for stable constructor wiring; detach handles animation now
    private final FoliaLib foliaLib;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof EnderChestHolder ecHolder)) return;

        sessions.detach(player, ecHolder);
    }
}

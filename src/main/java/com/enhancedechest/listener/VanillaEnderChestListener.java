package com.enhancedechest.listener;

import com.enhancedechest.service.ChestOpener;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

@RequiredArgsConstructor
public final class VanillaEnderChestListener implements Listener {

    private final ChestOpener opener;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        event.setCancelled(true);

        // Right-clicking an ender chest block always opens the GUI — no permission required.
        // The open-by-command permission only gates /enderchest and /eclist.
        Player player = event.getPlayer();
        Location blockLoc = block.getLocation();

        // The lid animation is driven by the inventory open/close lifecycle (see ChestOpener /
        // EnderChestGuiListener), not eagerly here: a single chest opens straight to its inventory,
        // whereas several chests open a dialog first — and dialogs have no close event to pair an
        // eager open() with, which would otherwise leave the lid stuck open.
        opener.open(player, blockLoc);
    }
}

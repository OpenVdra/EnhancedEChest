package com.enhancedechest.listener;

import com.enhancedechest.gui.EnderChestAnimator;
import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.tcoded.folialib.FoliaLib;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

@RequiredArgsConstructor
public final class EnderChestGuiListener implements Listener {

    private final EnderChestService service;
    private final FoliaLib foliaLib;
    private final LanguageManager lang;

    /**
     * Temporary chests are take-only: items may be removed but never added. Cancels any click that
     * would deposit into the temp (top) inventory — placing from the cursor, swapping, hotbar swaps,
     * and shift-clicking from the player inventory — while leaving pickups and shift-clicks out of the
     * temp chest untouched.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;
        if (holder.getKind() != ChestKind.TEMP) return;

        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        boolean deposit = switch (event.getAction()) {
            // Cursor → a clicked top slot.
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                 HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> clicked != null && clicked.equals(top);
            // Shift-click from the player inventory moves the stack into the temp chest.
            case MOVE_TO_OTHER_INVENTORY -> clicked != null && !clicked.equals(top);
            default -> false;
        };
        if (deposit) {
            event.setCancelled(true);
            notifyTakeOnly(event.getWhoClicked());
        }
    }

    /** Cancels any drag that would spread items into the temp (top) inventory slots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnderChestHolder holder)) return;
        if (holder.getKind() != ChestKind.TEMP) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) { // a raw slot below topSize belongs to the top inventory
                event.setCancelled(true);
                notifyTakeOnly(event.getWhoClicked());
                return;
            }
        }
    }

    private void notifyTakeOnly(org.bukkit.entity.HumanEntity who) {
        if (who instanceof Player p) {
            p.sendActionBar(lang.get("chest.temp-take-only"));
        }
    }

    /**
     * Saves inventory contents to DB on every close, regardless of close reason.
     * This fires for normal closes, /ec reopens (reason OPEN_NEW), and forced closes
     * from server-side events. The DB write is always correct because:
     * - On reopen via /ec: save fires here first, then EnderChestService.open() waits
     *   for the pending save before loading the fresh snapshot. No stale state.
     * - On quit: PlayerQuitListener fires save independently; both are idempotent.
     *
     * Close animation: dispatched to the block's region thread via runAtLocation so
     * the NMS call is always on the correct thread (required for Folia).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof EnderChestHolder ecHolder)) return;

        service.save(ecHolder, top);

        Location sourceBlock = ecHolder.getSourceBlock();
        if (sourceBlock != null) {
            Player player = (Player) event.getPlayer();
            foliaLib.getScheduler().runAtLocation(sourceBlock, task ->
                    EnderChestAnimator.close(player, sourceBlock));
        }
    }
}

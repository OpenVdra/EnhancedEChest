package com.enhancedechest.gui;

import com.enhancedechest.model.ChestKind;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Marker holder attached to every custom enderchest Inventory.
 * Listeners identify our GUIs with: inventory.getHolder() instanceof EnderChestHolder
 */
@Getter
public final class EnderChestHolder implements InventoryHolder {

    private final UUID owner;

    /** 1-based index of which of the owner's chests this inventory represents. */
    private final int index;

    /** Slot count of this chest (multiple of 9, 9..54) — the inventory was created at this size. */
    private final int size;

    /** Whether this chest is a normal or a temporary (overflow) chest; drives temp auto-delete on empty. */
    private final ChestKind kind;

    /**
     * Location of the ender chest block if the GUI was opened by right-clicking a block.
     * Null when opened via /ec command or the management dialog (no physical block involved).
     * Used to play the open/close animation on the block.
     */
    @Nullable
    private final Location sourceBlock;

    public EnderChestHolder(UUID owner, int index, int size, ChestKind kind, @Nullable Location sourceBlock) {
        this.owner = owner;
        this.index = index;
        this.size = size;
        this.kind = kind;
        this.sourceBlock = sourceBlock;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("EnderChestHolder does not hold an Inventory reference");
    }
}

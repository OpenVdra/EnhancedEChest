package com.enhancedechest.service;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Item-moving chest operations: shrink-spill, delete (spill or force), and bulk delete. Each first
 * force-closes every viewer's GUI via {@link ChestSessionManager#forceCloseAll} (flushing the live
 * session), then runs the load-decode-split exclusively per (owner, index) via
 * {@link ChestSessionManager#runExclusive} so no concurrent open sees a half-applied state — keeping
 * the swaps dupe-safe even with multiple concurrent viewers. Drives {@code /ee resize}, {@code /ee
 * delete}, and the expiry sweeper.
 */
public final class ChestSpillService {

    private final ChestSessionManager sessions;
    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final StorageGateway storageGateway;

    // Runtime-tunable via /ee reload (see setTempExpiry). volatile so the value written on the main
    // thread during a reload is visible to the async threads that stamp a freshly spilled temp chest.
    /** Lifetime, in milliseconds, of a temp chest created when items spill on shrink/delete/expire. */
    private volatile long tempExpiryMillis;

    public ChestSpillService(ChestSessionManager sessions, EnderChestStorage storage,
                             ContainerCodec codec, StorageGateway storageGateway,
                             long tempExpiryMillis) {
        this.sessions         = sessions;
        this.storage          = storage;
        this.codec            = codec;
        this.storageGateway   = storageGateway;
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /**
     * Re-applies the runtime-tunable temp-chest lifetime after a {@code /ee reload}. Only affects temp
     * chests stamped <i>after</i> this call, so it is dupe-safe to set on the main thread while async
     * storage work is pending.
     */
    public void setTempExpiry(long tempExpiryMillis) {
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /**
     * Resizes a chest, spilling any cut-off items into a temp chest if it is shrunk below its used
     * slots. Force-closes every viewer's GUI first (flushing the live session), then runs the
     * load-decode-split exclusively per (owner, index) so no concurrent open sees a half-applied state.
     * A grow, or a shrink that loses no items, is a plain resize.
     */
    public CompletableFuture<Void> resizeOrSpill(UUID owner, int index, int newSize) {
        return sessions.forceCloseAll(owner, index).thenCompose(v -> sessions.runExclusive(owner, index, () -> {
            EnderChestData data = storage.loadChest(owner, index);
            if (data == null) return null;

            ItemStack[] all = decodeAll(data);
            // Nothing occupies a slot at or beyond newSize → a plain resize loses no items.
            if (lastUsedSlot(all) < newSize) {
                storage.resizeChest(owner, index, newSize);
                return null;
            }

            ItemStack[] visible  = Arrays.copyOfRange(all, 0, newSize);
            ItemStack[] overflow = Arrays.copyOfRange(all, newSize, all.length);
            byte[] visibleBytes  = codec.encode(visible);
            byte[] overflowBytes = codec.encode(overflow);
            storage.spillShrink(owner, index, newSize, visibleBytes, overflowBytes,
                    requiredTempSize(overflow), System.currentTimeMillis() + tempExpiryMillis);
            return null;
        }));
    }

    /**
     * Removes a chest. With {@code force} the row is hard-deleted (items lost immediately); otherwise
     * any items are spilled into a temp chest first. Force-closes every viewer's GUI, then performs the
     * delete exclusively per (owner, index) so the swap is dupe-safe. Used by {@code /ee delete} and
     * by the expiry sweeper (NORMAL → spill, TEMP → force).
     */
    public CompletableFuture<Void> removeChest(UUID owner, int index, boolean force) {
        return sessions.forceCloseAll(owner, index).thenCompose(v -> sessions.runExclusive(owner, index, () -> {
            if (force) {
                storage.deleteChest(owner, index);
                return null;
            }
            EnderChestData data = storage.loadChest(owner, index);
            byte[] items = null;
            int tempSize = 0;
            if (data != null && data.containerData() != null && data.containerData().length > 0
                    && lastUsedSlot(decodeAll(data)) >= 0) {
                // Reuse the already-encoded bytes; the temp chest mirrors the original chest's size.
                items = data.containerData();
                tempSize = data.size();
            }
            storage.spillRemove(owner, index, items, tempSize,
                    System.currentTimeMillis() + tempExpiryMillis);
            return null;
        }));
    }

    /**
     * Bulk-removes the {@code count} newest (highest-index) NORMAL chests a player owns, spilling (or
     * force-discarding) each, and completes with the number actually removed. The player's <i>first</i>
     * chest — the lowest-indexed NORMAL chest — is always protected, so a player can never be left with
     * no chests; deleting fewer than {@code count} when that is all that is eligible is not an error.
     * Temp chests are ignored (they are transient and expire on their own).
     *
     * <p>Targets are snapshotted up front, then deleted sequentially: a spilling delete creates a fresh
     * temp chest at a higher index, but those are not in the target list so they are never re-touched.
     * Each per-index delete still serializes behind its own pending saves and force-closes open GUIs,
     * so the bulk op is dupe-safe.
     */
    public CompletableFuture<Integer> removeNewestChests(UUID owner, int count, boolean force) {
        return storageGateway.listChestsAsync(owner).thenCompose(chests -> {
            // Only NORMAL chests are eligible; sorted ascending so element 0 is the protected first chest.
            List<ChestSummary> normal = chests.stream()
                    .filter(c -> c.kind() == ChestKind.NORMAL)
                    .sorted(Comparator.comparingInt(ChestSummary::index))
                    .toList();
            if (normal.size() <= 1) {
                return CompletableFuture.completedFuture(0);
            }
            // Everything except the protected first chest, newest (highest index) first, capped at count.
            List<Integer> targets = normal.subList(1, normal.size()).stream()
                    .map(ChestSummary::index)
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(0, count))
                    .toList();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int index : targets) {
                chain = chain.thenCompose(v -> removeChest(owner, index, force));
            }
            return chain.thenApply(v -> targets.size());
        });
    }

    /** Decodes a chest's contents to a full MAX_SIZE array so all stored slots are visible. */
    private ItemStack[] decodeAll(EnderChestData data) {
        if (data.containerData() == null || data.containerData().length == 0) {
            ItemStack[] empty = new ItemStack[ContainerCodec.MAX_SIZE];
            Arrays.fill(empty, ItemStack.empty());
            return empty;
        }
        try {
            return codec.decode(data.containerData(), ContainerCodec.MAX_SIZE);
        } catch (CodecException e) {
            // Abort the spill rather than risk losing items to a bad decode; surfaces as a failure.
            throw new RuntimeException("Codec failure during spill for chest " + data.index(), e);
        }
    }

    /** Highest slot index holding a non-empty item, or -1 if the array is entirely empty. */
    private static int lastUsedSlot(ItemStack[] items) {
        for (int i = items.length - 1; i >= 0; i--) {
            if (!isEmpty(items[i])) return i;
        }
        return -1;
    }

    /** Smallest valid chest size (multiple of 9, 9..54) that holds every non-empty slot in {@code items}. */
    private static int requiredTempSize(ItemStack[] items) {
        int last = lastUsedSlot(items);
        if (last < 0) return ContainerCodec.SLOT_STEP;
        int needed = ((last / ContainerCodec.SLOT_STEP) + 1) * ContainerCodec.SLOT_STEP;
        return Math.max(ContainerCodec.SLOT_STEP, Math.min(ContainerCodec.MAX_SIZE, needed));
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}

package com.enhancedechest.gui;

import com.enhancedechest.gui.dialog.ChestDialogs;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Owns the open and save lifecycle of the custom ender chest GUIs, now multi-chest.
 *
 * <p>/enderchest and right-click open the player's primary chest (auto-creating chest #1 if the
 * player owns none). /eclist opens a management dialog; other chests are reached from there.
 *
 * <p>Dupe-safety contract (unchanged in spirit, now per chest index):
 * <ul>
 *   <li>opening always closes any existing GUI first (sync, entity thread), then waits for any
 *       in-flight async DB save <i>of that same chest</i> before loading fresh data.</li>
 *   <li>save() encodes inventory bytes synchronously on the calling thread, then flushes to DB
 *       on a daemon thread, keyed by (owner, index).</li>
 *   <li>flushPendingSaves() in onDisable() blocks until all writes finish before the pool closes.</li>
 * </ul>
 */
public final class EnderChestService {

    /** Permission to open the ender chest by command; also gates the dialog's "set as main" action. */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";

    /** Identifies an in-flight save by owner + chest index, so unrelated chests never block each other. */
    private record SaveKey(UUID owner, int index) {}

    private final LanguageManager lang;
    private final ContainerCodec  codec;
    private final EnderChestStorage storage;
    private final Logger logger;
    private final FoliaLib foliaLib;
    private final ChestDialogs dialogs;

    /** Size of the chest auto-created the first time a player ever opens their ender chest. */
    private final int defaultSize;

    /** Lifetime, in milliseconds, of a temp chest created when items spill on shrink/delete/expire. */
    private final long tempExpiryMillis;

    private final ConcurrentHashMap<SaveKey, CompletableFuture<Void>> pendingSaves =
            new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EnhancedEChest-db");
        t.setDaemon(true);
        return t;
    });

    public EnderChestService(LanguageManager lang, ContainerCodec codec,
                             EnderChestStorage storage, Logger logger, FoliaLib foliaLib,
                             int defaultSize, long tempExpiryMillis) {
        this.lang             = lang;
        this.codec            = codec;
        this.storage          = storage;
        this.logger           = logger;
        this.foliaLib         = foliaLib;
        this.defaultSize      = defaultSize;
        this.tempExpiryMillis = tempExpiryMillis;
        this.dialogs          = new ChestDialogs(this, lang);
    }

    // ---- opening ----

    /**
     * Default open entry point for {@code /enderchest} and right-click:
     * <ul>
     *   <li>0 or 1 chest — opens the primary chest directly (creating chest #1 if the player owns none);</li>
     *   <li>2+ chests — opens the management list dialog so the player picks which to open.</li>
     * </ul>
     *
     * @param sourceBlock ender chest block location if opened via right-click; null for command/dialog
     */
    public void open(Player player, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {
            closeExistingGui(player);
            listChestsAsync(uuid)
                    .thenAccept(chests -> {
                        if (chests.size() >= 2) {
                            foliaLib.getScheduler().runAtEntity(player, task -> {
                                if (player.isOnline()) player.showDialog(dialogs.listDialog(chests, canSetMain, sourceBlock));
                            });
                        } else {
                            openPrimaryChest(player, uuid, sourceBlock);
                        }
                    })
                    .exceptionally(e -> reportOpenFailure(player, e));
        });
    }

    /** Loads and opens the player's primary chest inventory directly (creating chest #1 if none exist). */
    private void openPrimaryChest(Player player, UUID uuid, @Nullable Location sourceBlock) {
        CompletableFuture
                .supplyAsync(() -> resolvePrimaryIndex(uuid), asyncExecutor)
                .thenCompose(index -> waitPending(uuid, index).thenApply(v -> index))
                .thenCompose(index -> CompletableFuture.supplyAsync(
                        () -> storage.loadChest(uuid, index), asyncExecutor))
                .thenAccept(data -> openLoaded(player, data, sourceBlock))
                .exceptionally(e -> reportOpenFailure(player, e));
    }

    /**
     * Opens a chest selected by a free-text query from {@code /ec <chest>}:
     * <ul>
     *   <li>{@code #N} (or a bare positive integer) opens the chest with that index;</li>
     *   <li>anything else is matched case-insensitively against players' custom chest names.</li>
     * </ul>
     * A miss reports {@code chest.unknown} rather than silently opening the primary chest.
     */
    public void openByQuery(Player player, String query) {
        String trimmed = query.trim();
        Integer index = parseIndexQuery(trimmed);
        if (index != null) {
            openChest(player, index, null);
            return;
        }
        UUID uuid = player.getUniqueId();
        listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    chests.stream()
                            .filter(c -> c.customName() != null
                                    && c.customName().equalsIgnoreCase(trimmed))
                            .findFirst()
                            .ifPresentOrElse(
                                    c -> openChest(player, c.index(), null),
                                    () -> player.sendMessage(lang.get("chest.unknown", "query", trimmed)));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    /** Parses a {@code #N} or bare-integer index query; returns null for non-index input. */
    private static Integer parseIndexQuery(String query) {
        String digits = query.startsWith("#") ? query.substring(1) : query;
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int value = Integer.parseInt(digits);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Opens a specific chest by index (from the management dialog). */
    public void openChest(Player player, int index, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {
            closeExistingGui(player);
            waitPending(uuid, index)
                    .thenCompose(v -> CompletableFuture.supplyAsync(
                            () -> storage.loadChest(uuid, index), asyncExecutor))
                    .thenAccept(data -> openLoaded(player, data, sourceBlock))
                    .exceptionally(e -> reportOpenFailure(player, e));
        });
    }

    private int resolvePrimaryIndex(UUID uuid) {
        int index = storage.getPrimaryIndex(uuid);
        if (index == -1) {
            // First-ever access: createChest flags this first chest as primary automatically.
            index = storage.createChest(uuid, defaultSize);
        }
        return index;
    }

    private void openLoaded(Player player, @Nullable EnderChestData data, @Nullable Location sourceBlock) {
        foliaLib.getScheduler().runAtEntity(player, task -> {
            if (!player.isOnline()) return;
            if (data == null) {
                player.sendMessage(lang.get("chest.not-found"));
                return;
            }
            doOpenInventory(player, data, sourceBlock);
        });
    }

    private void doOpenInventory(Player player, EnderChestData data, @Nullable Location sourceBlock) {
        int size = data.size();
        Component title = lang.getChestLabel(data.index(), data.customName(), data.kind());
        Inventory inv = Bukkit.createInventory(
                new EnderChestHolder(data.owner(), data.index(), size, data.kind(), sourceBlock),
                size, title);

        if (data.containerData() != null && data.containerData().length > 0) {
            try {
                inv.setContents(codec.decode(data.containerData(), size));
            } catch (CodecException e) {
                logger.error("Codec failure for {} chest {} — aborting open to protect stored data",
                        player.getName(), data.index(), e);
                player.sendMessage(lang.get("chest.codec-failed"));
                return;
            }
        }
        player.openInventory(inv);

        // Play the open lid animation now that the inventory is actually showing; the matching close
        // animation fires from EnderChestGuiListener on InventoryCloseEvent. Both are gated on a
        // source block, so command/dialog opens (no block) animate nothing. Dispatched to the block's
        // region thread, as the animation touches the block entity (required on Folia).
        if (sourceBlock != null) {
            foliaLib.getScheduler().runAtLocation(sourceBlock, task ->
                    EnderChestAnimator.open(player, sourceBlock));
        }
    }

    private void closeExistingGui(Player player) {
        Inventory currentTop = player.getOpenInventory().getTopInventory();
        if (currentTop.getHolder() instanceof EnderChestHolder) {
            player.closeInventory();
        }
    }

    private Void reportOpenFailure(Player player, Throwable e) {
        logger.error("Failed to load enderchest for {} — aborting open", player.getName(),
                e.getCause() != null ? e.getCause() : e);
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (player.isOnline()) player.sendMessage(lang.get("chest.load-failed"));
        });
        return null;
    }

    // ---- management dialog ----

    /** Loads the player's chests and shows the /eclist management dialog. */
    public void openListDialog(Player player) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    if (chests.isEmpty()) {
                        player.sendMessage(lang.get("chest.none"));
                        return;
                    }
                    player.showDialog(dialogs.listDialog(chests, canSetMain, null));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    /** Shows the per-chest detail dialog (Open / Rename / Set-main / Back). */
    public void openDetailDialog(Player player, int index) {
        boolean canSetMain = canSetMain(player);
        showChestDialog(player, index, chest -> dialogs.detailDialog(chest, canSetMain, null));
    }

    /**
     * Whether the player may set a chest as their main: gated on the open-by-command permission,
     * since the "main" chest only matters when {@code /enderchest} can be used to open it.
     */
    private boolean canSetMain(Player player) {
        return player.hasPermission(OPEN_GUI_PERMISSION);
    }

    /** Shows the dedicated rename dialog for a chest. */
    public void openRenameDialog(Player player, int index) {
        showChestDialog(player, index, dialogs::renameDialog);
    }

    /** Loads the chest by index and shows a dialog built from it on the player's thread. */
    private void showChestDialog(Player player, int index,
                                 java.util.function.Function<ChestSummary, io.papermc.paper.dialog.Dialog> builder) {
        UUID uuid = player.getUniqueId();
        listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    chests.stream().filter(c -> c.index() == index).findFirst().ifPresentOrElse(
                            c -> player.showDialog(builder.apply(c)),
                            () -> player.sendMessage(lang.get("chest.not-found")));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    // ---- saving ----

    /**
     * Saves the inventory to the database asynchronously, keyed by (owner, chest index).
     * Encodes bytes synchronously on the calling (entity) thread, then writes on a daemon thread.
     */
    public void save(EnderChestHolder holder, Inventory inventory) {
        UUID uuid = holder.getOwner();
        int index = holder.getIndex();

        // A temp chest that the player has fully emptied removes itself rather than persisting an
        // empty row (the emptiness check is a fast in-memory scan; no decode needed). Serialized via
        // runExclusive so a concurrent open waits, like any other item-moving operation.
        if (holder.getKind() == ChestKind.TEMP && isInventoryEmpty(inventory)) {
            runExclusive(uuid, index, () -> { storage.deleteChest(uuid, index); return null; })
                    .exceptionally(e -> {
                        logger.error("Failed to remove emptied temp chest {} for {}", index, uuid, e);
                        return null;
                    });
            return;
        }

        byte[] encoded;
        try {
            encoded = codec.encode(inventory.getContents());
        } catch (Exception e) {
            logger.error("Codec encode failure for {} chest {} — data NOT saved to prevent corruption",
                    uuid, index, e);
            return;
        }

        SaveKey key = new SaveKey(uuid, index);
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                storage.saveChest(uuid, index, encoded);
            } catch (Exception e) {
                logger.error("DB save failure for {} chest {}", uuid, index, e);
            }
        }, asyncExecutor);

        pendingSaves.put(key, future);
        future.whenComplete((v, e) -> pendingSaves.remove(key, future));
    }

    private CompletableFuture<Void> waitPending(UUID uuid, int index) {
        return pendingSaves.getOrDefault(new SaveKey(uuid, index),
                CompletableFuture.completedFuture(null));
    }

    // ---- async storage operations (used by dialog callbacks and admin commands) ----

    public CompletableFuture<List<ChestSummary>> listChestsAsync(UUID owner) {
        return CompletableFuture.supplyAsync(() -> storage.listChests(owner), asyncExecutor);
    }

    public CompletableFuture<Integer> createChestAsync(UUID owner, int size) {
        return createChestAsync(owner, size, null);
    }

    /** Creates a NORMAL chest, expirable if {@code expiresAt} is non-null (epoch millis). */
    public CompletableFuture<Integer> createChestAsync(UUID owner, int size, @Nullable Long expiresAt) {
        return CompletableFuture.supplyAsync(() -> storage.createChest(owner, size, expiresAt), asyncExecutor);
    }

    /** Convenience for an expirable granted chest: expires {@code durationMillis} from now. */
    public CompletableFuture<Integer> addExpirableChest(UUID owner, int size, long durationMillis) {
        return createChestAsync(owner, size, System.currentTimeMillis() + durationMillis);
    }

    public CompletableFuture<Void> renameAsync(UUID owner, int index, @Nullable String name) {
        return CompletableFuture.runAsync(() -> storage.renameChest(owner, index, name), asyncExecutor);
    }

    public CompletableFuture<Void> setPrimaryAsync(UUID owner, int index) {
        return CompletableFuture.runAsync(() -> storage.setPrimary(owner, index), asyncExecutor);
    }

    /** Runs the given action on the player's entity thread (helper for command/dialog callbacks). */
    public void runForPlayer(Player player, Runnable action) {
        foliaLib.getScheduler().runAtEntity(player, task -> action.run());
    }

    // ---- item-moving operations (shrink spill / delete spill / expiry) ----

    /**
     * Resizes a chest, spilling any cut-off items into a temp chest if it is shrunk below its used
     * slots. Force-closes the owner's GUI first (flushing its save), then runs the load-decode-split
     * exclusively per (owner, index) so no concurrent open sees a half-applied state. A grow, or a
     * shrink that loses no items, is a plain resize.
     */
    public CompletableFuture<Void> resizeOrSpill(UUID owner, int index, int newSize) {
        return forceCloseIfOpen(owner, index).thenCompose(v -> runExclusive(owner, index, () -> {
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
     * any items are spilled into a temp chest first. Force-closes the owner's GUI, then performs the
     * delete exclusively per (owner, index) so the swap is dupe-safe. Used by {@code /ee delete} and
     * by the expiry sweeper (NORMAL → spill, TEMP → force).
     */
    public CompletableFuture<Void> removeChest(UUID owner, int index, boolean force) {
        return forceCloseIfOpen(owner, index).thenCompose(v -> runExclusive(owner, index, () -> {
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
     * Serializes arbitrary DB work for one (owner, index) behind any in-flight save/op for that key,
     * registering it in {@code pendingSaves} so a concurrent {@code open} waits for it. The work runs
     * on the async executor; the returned future completes with its result (or its failure).
     */
    private <T> CompletableFuture<T> runExclusive(UUID owner, int index, Supplier<T> dbWork) {
        SaveKey key = new SaveKey(owner, index);
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> marker = result.handle((v, e) -> null);
        // Atomically chain after whatever is currently pending for this key.
        pendingSaves.compute(key, (k, prev) -> {
            CompletableFuture<Void> base = (prev != null) ? prev : CompletableFuture.completedFuture(null);
            base.whenComplete((v, e) ->
                    CompletableFuture.supplyAsync(dbWork, asyncExecutor).whenComplete((r, err) -> {
                        if (err != null) result.completeExceptionally(err);
                        else result.complete(r);
                    }));
            return marker;
        });
        marker.whenComplete((v, e) -> pendingSaves.remove(key, marker));
        return result;
    }

    /**
     * If the owner is online with exactly that chest open, closes it on their entity thread (which
     * fires the InventoryCloseEvent → {@code save()} synchronously, registering its pending future).
     * The returned future completes once the close has been dispatched, so the caller can then chain
     * an exclusive op that will serialize behind the just-registered save.
     */
    private CompletableFuture<Void> forceCloseIfOpen(UUID owner, int index) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null || !player.isOnline()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        foliaLib.getScheduler().runAtEntity(player, task -> {
            try {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof EnderChestHolder h
                        && h.getOwner().equals(owner) && h.getIndex() == index) {
                    player.closeInventory();
                }
            } finally {
                done.complete(null);
            }
        });
        return done;
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

    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (!isEmpty(item)) return false;
        }
        return true;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    // ---- shutdown ----

    public void flushPendingSaves() {
        if (pendingSaves.isEmpty()) return;
        try {
            CompletableFuture.allOf(pendingSaves.values().toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Timed out waiting for pending DB saves on shutdown — some data may be lost", e);
        }
    }

    public void shutdown() {
        flushPendingSaves();
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

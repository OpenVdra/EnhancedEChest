package com.enhancedechest.service;

import com.enhancedechest.gui.EnderChestAnimator;
import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Owns the shared-live-inventory registry and the dupe-safety contract for every open chest: this is
 * the single closed class that mutates the {@link #sessions} map, and it does so only on the
 * {@link #onGlobal} bookkeeping thread. Every chest open funnels through {@link #open} (the sole funnel
 * point); concurrent admin operations chain behind {@link #forceCloseAll} + {@link #runExclusive}.
 *
 * <h2>Shared live inventory (concurrent-edit) model</h2>
 * Every open chest is backed by a single shared {@link Inventory} held in {@link #sessions}, keyed by
 * (owner, index). Owner and admin {@code openInventory()} the <i>same</i> object, so Bukkit serialises
 * all item moves on one {@code ItemStack[]} — making item-level duping between concurrent viewers
 * structurally impossible on a single-threaded platform.
 *
 * <p><b>Folia caveat:</b> two viewers may live on different region threads, where a shared inventory is
 * unsafe. On Folia we therefore allow only <b>one</b> live viewer per chest (a second opener is denied);
 * on Paper concurrent editing is fully supported.
 *
 * <p>All session bookkeeping (the {@link #sessions} map, viewer sets, attach/detach/persist decisions)
 * runs on a single thread via {@link #onGlobal}: the main thread on Paper, the global region thread on
 * Folia. This removes registry-level races on both. The actual DB read/write stays async; encoding is
 * synchronous and only ever happens once all viewers have closed (no concurrent edit during encode).
 *
 * <p>Dupe-safety contract (preserved, now per shared session):
 * <ul>
 *   <li>the <i>first</i> open of a chest waits for any in-flight async save of that same chest, then
 *       loads fresh from the DB; subsequent opens attach to the live session and never re-read the DB
 *       while it is open (the live inventory is authoritative).</li>
 *   <li>the chest is persisted when its <i>last</i> viewer closes (or a force-close fires), encoding the
 *       shared contents synchronously on the global thread then flushing to the DB on a daemon thread,
 *       keyed by (owner, index).</li>
 *   <li>flushPendingSaves() in onDisable() blocks until all writes finish before the pool closes.</li>
 * </ul>
 */
public final class ChestSessionManager {

    /** Identifies an in-flight save by owner + chest index, so unrelated chests never block each other. */
    private record SaveKey(UUID owner, int index) {}

    /** A queued open waiting for its session's first DB load to finish. */
    private record Pending(Player player, @Nullable Location sourceBlock) {}

    /**
     * A live shared chest inventory and its current viewers. All fields are read and written only on the
     * {@link #onGlobal} thread, so no per-field synchronization is needed.
     */
    private static final class Session {
        final UUID owner;
        final int index;
        ChestKind kind;
        @Nullable Inventory inv;                 // null until the first DB load completes
        boolean ready;                           // inv is populated and viewers may attach
        boolean closing;                         // a force-close is persisting; new attaches are rejected
        final Set<UUID> viewers = new HashSet<>();
        final Map<UUID, Location> viewerBlocks = new HashMap<>();  // per-viewer source block for lid animation
        final List<Pending> waiting = new ArrayList<>();           // opens queued until ready

        Session(UUID owner, int index) {
            this.owner = owner;
            this.index = index;
        }
    }

    private final LanguageManager lang;
    private final ContainerCodec  codec;
    private final EnderChestStorage storage;
    private final Logger logger;
    private final FoliaLib foliaLib;
    private final DbExecutor db;

    private final ConcurrentHashMap<SaveKey, CompletableFuture<Void>> pendingSaves =
            new ConcurrentHashMap<>();

    /** Live shared sessions, keyed by (owner, index). Mutated only on the {@link #onGlobal} thread. */
    private final ConcurrentHashMap<SaveKey, Session> sessions = new ConcurrentHashMap<>();

    public ChestSessionManager(LanguageManager lang, ContainerCodec codec,
                               EnderChestStorage storage, Logger logger, FoliaLib foliaLib,
                               DbExecutor db) {
        this.lang     = lang;
        this.codec    = codec;
        this.storage  = storage;
        this.logger   = logger;
        this.foliaLib = foliaLib;
        this.db       = db;
    }

    /**
     * Runs {@code task} on the single bookkeeping thread (main on Paper, global region on Folia),
     * inline when already on it. All {@link #sessions} mutations funnel through here so the registry is
     * race-free across both platforms.
     */
    private void onGlobal(Runnable task) {
        if (foliaLib.getScheduler().isGlobalTickThread()) {
            task.run();
        } else {
            foliaLib.getScheduler().runNextTick(t -> task.run());
        }
    }

    // ---- opening ----

    /**
     * The single funnel through which every chest open passes. Closes the player's current chest GUI on
     * their entity thread (flushing its session), then hands off to {@link #decideOpen} on the global
     * bookkeeping thread to attach to — or create — the live session for {@code (owner, index)}.
     */
    public void open(Player player, UUID owner, int index, @Nullable Location sourceBlock) {
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (!player.isOnline()) return;
            closeExistingGui(player);
            onGlobal(() -> decideOpen(player, owner, index, sourceBlock));
        });
    }

    /** Global-thread decision: attach to an existing live session, or create one and load it fresh. */
    private void decideOpen(Player player, UUID owner, int index, @Nullable Location sourceBlock) {
        SaveKey key = new SaveKey(owner, index);
        UUID viewer = player.getUniqueId();
        Session existing = sessions.get(key);
        if (existing != null && !existing.closing) {
            if (foliaLib.isFolia() && isOccupiedByOther(existing, viewer)) {
                notifyOnPlayer(player, "chest.in-use");
                return;
            }
            if (existing.ready) {
                addViewerAndOpen(player, existing, sourceBlock);
            } else {
                existing.waiting.add(new Pending(player, sourceBlock));
            }
            return;
        }

        // No live session: create one and load fresh after any in-flight save for this key.
        Session created = new Session(owner, index);
        created.waiting.add(new Pending(player, sourceBlock));
        sessions.put(key, created);
        waitPending(owner, index)
                .thenCompose(v -> db.supply(() -> storage.loadChest(owner, index)))
                .whenComplete((data, err) -> onGlobal(() -> finishCreate(key, created, data, err)));
    }

    /** True if a viewer (or a queued opener) other than {@code self} already holds this session. */
    private static boolean isOccupiedByOther(Session s, UUID self) {
        for (UUID u : s.viewers) {
            if (!u.equals(self)) return true;
        }
        for (Pending p : s.waiting) {
            if (!p.player().getUniqueId().equals(self)) return true;
        }
        return false;
    }

    /** Global-thread completion of a first load: build the shared inventory and flush the waiting queue. */
    private void finishCreate(SaveKey key, Session created,
                              @Nullable EnderChestData data, @Nullable Throwable err) {
        List<Pending> waiters = new ArrayList<>(created.waiting);
        created.waiting.clear();

        // A force-close (admin resize/delete) may have superseded this session while the load was in flight.
        boolean stale = sessions.get(key) != created || created.closing;
        if (stale || err != null || data == null) {
            sessions.remove(key, created);
            for (Pending p : waiters) {
                if (err != null) reportOpenFailure(p.player(), err);
                else notifyOnPlayer(p.player(), "chest.not-found");
            }
            return;
        }

        Inventory inv = buildSharedInventory(data);
        if (inv == null) {
            sessions.remove(key, created);
            for (Pending p : waiters) notifyOnPlayer(p.player(), "chest.codec-failed");
            return;
        }
        created.kind  = data.kind();
        created.inv   = inv;
        created.ready = true;
        for (Pending p : waiters) addViewerAndOpen(p.player(), created, p.sourceBlock());
    }

    /**
     * Builds the shared {@link Inventory} for a chest, decoding its stored contents. The holder carries
     * no source block (block animation is tracked per-viewer in the session). Returns null if the stored
     * bytes fail to decode, so the caller can abort the open rather than risk corrupting the data.
     */
    private @Nullable Inventory buildSharedInventory(EnderChestData data) {
        int size = data.size();
        Component title = lang.getChestLabel(data.index(), data.customName(), data.kind());
        Inventory inv = Bukkit.createInventory(
                new EnderChestHolder(data.owner(), data.index(), size, data.kind(), null), size, title);

        if (data.containerData() != null && data.containerData().length > 0) {
            try {
                inv.setContents(codec.decode(data.containerData(), size));
            } catch (CodecException e) {
                logger.error("Codec failure for {} chest {} — aborting open to protect stored data",
                        data.owner(), data.index(), e);
                return null;
            }
        }
        return inv;
    }

    /**
     * Global-thread: registers the player as a viewer of the live session, then opens the shared
     * inventory for them on their entity thread (playing the lid animation if opened from a block).
     */
    private void addViewerAndOpen(Player player, Session s, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        s.viewers.add(uuid);
        if (sourceBlock != null) s.viewerBlocks.put(uuid, sourceBlock);
        Inventory inv = s.inv;
        foliaLib.getScheduler().runAtEntity(player, task -> {
            if (!player.isOnline() || inv == null) {
                onGlobal(() -> removeViewer(s, uuid));
                return;
            }
            player.openInventory(inv);
            if (sourceBlock != null) {
                foliaLib.getScheduler().runAtLocation(sourceBlock, lt ->
                        EnderChestAnimator.open(player, sourceBlock));
            }
        });
    }

    /**
     * Global-thread: drops a viewer that never actually opened (offline by the time the open ran),
     * persisting and tearing down the session if it leaves no viewers behind. No animation (the chest
     * was never shown to this viewer).
     */
    private void removeViewer(Session s, UUID uuid) {
        s.viewers.remove(uuid);
        s.viewerBlocks.remove(uuid);
        if (!s.closing && s.viewers.isEmpty() && s.waiting.isEmpty()) {
            sessions.remove(new SaveKey(s.owner, s.index), s);
            persist(s);
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

    /** Sends a localized message to the player on their entity thread (if still online). */
    private void notifyOnPlayer(Player player, String key) {
        foliaLib.getScheduler().runAtEntity(player, t -> {
            if (player.isOnline()) player.sendMessage(lang.get(key));
        });
    }

    // ---- closing / saving ----

    /**
     * Detaches a viewer when they close the shared GUI (called from the GUI close and quit listeners on
     * the player's entity thread). Removes them from the session on the global thread and, if they were
     * the <i>last</i> viewer, persists the shared contents. A no-op if the session was already torn down
     * by a force-close (which persists itself) — so the same close never double-saves.
     */
    public void detach(Player player, EnderChestHolder holder) {
        UUID uuid = player.getUniqueId();
        SaveKey key = new SaveKey(holder.getOwner(), holder.getIndex());
        onGlobal(() -> {
            Session s = sessions.get(key);
            if (s == null) return;                         // already force-closed and persisted

            boolean wasViewer = s.viewers.remove(uuid);
            Location block = s.viewerBlocks.remove(uuid);
            if (wasViewer && block != null) {
                foliaLib.getScheduler().runAtLocation(block, lt ->
                        EnderChestAnimator.close(player, block));
            }

            if (s.closing) return;                         // force-close path owns persistence
            if (s.viewers.isEmpty() && s.waiting.isEmpty()) {
                sessions.remove(key, s);
                persist(s);
            }
        });
    }

    /**
     * Persists a session's shared inventory to the database (must be called on the global thread, with
     * no viewer still editing). Encodes bytes synchronously, then writes on a daemon thread, keyed by
     * (owner, index) and registered in {@link #pendingSaves} so a concurrent open/op waits for it.
     * An emptied TEMP chest removes itself instead of persisting an empty row.
     */
    private void persist(Session s) {
        Inventory inv = s.inv;
        if (inv == null) return;                           // never became ready; nothing to save
        UUID owner = s.owner;
        int index = s.index;

        if (s.kind == ChestKind.TEMP && isInventoryEmpty(inv)) {
            runExclusive(owner, index, () -> { storage.deleteChest(owner, index); return null; })
                    .exceptionally(e -> {
                        logger.error("Failed to remove emptied temp chest {} for {}", index, owner, e);
                        return null;
                    });
            return;
        }

        byte[] encoded;
        try {
            encoded = codec.encode(inv.getContents());
        } catch (Exception e) {
            logger.error("Codec encode failure for {} chest {} — data NOT saved to prevent corruption",
                    owner, index, e);
            return;
        }

        SaveKey key = new SaveKey(owner, index);
        CompletableFuture<Void> future = db.run(() -> {
            try {
                storage.saveChest(owner, index, encoded);
            } catch (Exception e) {
                logger.error("DB save failure for {} chest {}", owner, index, e);
            }
        });

        pendingSaves.put(key, future);
        future.whenComplete((v, e) -> pendingSaves.remove(key, future));
    }

    private CompletableFuture<Void> waitPending(UUID uuid, int index) {
        return pendingSaves.getOrDefault(new SaveKey(uuid, index),
                CompletableFuture.completedFuture(null));
    }

    /**
     * Serializes arbitrary DB work for one (owner, index) behind any in-flight save/op for that key,
     * registering it in {@code pendingSaves} so a concurrent {@code open} waits for it. The work runs
     * on the async executor; the returned future completes with its result (or its failure).
     */
    public <T> CompletableFuture<T> runExclusive(UUID owner, int index, Supplier<T> dbWork) {
        SaveKey key = new SaveKey(owner, index);
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> marker = result.handle((v, e) -> null);
        // Atomically chain after whatever is currently pending for this key.
        pendingSaves.compute(key, (k, prev) -> {
            CompletableFuture<Void> base = (prev != null) ? prev : CompletableFuture.completedFuture(null);
            base.whenComplete((v, e) ->
                    db.supply(dbWork).whenComplete((r, err) -> {
                        if (err != null) result.completeExceptionally(err);
                        else result.complete(r);
                    }));
            return marker;
        });
        marker.whenComplete((v, e) -> pendingSaves.remove(key, marker));
        return result;
    }

    /**
     * Force-closes the GUI of <b>every</b> viewer of {@code (owner, index)}, then persists the shared
     * contents and tears down the session — returning a future that completes once the save has been
     * registered in {@link #pendingSaves}. The caller can then chain an exclusive op that serialises
     * behind that save, keeping admin resize/delete dupe-safe even with multiple concurrent viewers.
     *
     * <p>The persist runs only after all viewer screens have actually closed (their close handlers see
     * {@code closing} and skip their own save), so the shared inventory is read with no viewer still
     * editing it — safe to encode on the global thread even on Folia.
     */
    public CompletableFuture<Void> forceCloseAll(UUID owner, int index) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        onGlobal(() -> {
            SaveKey key = new SaveKey(owner, index);
            Session s = sessions.get(key);
            if (s == null) {
                done.complete(null);
                return;
            }
            s.closing = true;
            List<CompletableFuture<?>> closes = new ArrayList<>();
            for (UUID uuid : new ArrayList<>(s.viewers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                CompletableFuture<Void> c = new CompletableFuture<>();
                closes.add(c);
                foliaLib.getScheduler().runAtEntity(p, t -> {
                    try {
                        Inventory top = p.getOpenInventory().getTopInventory();
                        if (top.getHolder() instanceof EnderChestHolder h
                                && h.getOwner().equals(owner) && h.getIndex() == index) {
                            p.closeInventory();
                        }
                    } finally {
                        c.complete(null);
                    }
                });
            }
            CompletableFuture.allOf(closes.toArray(new CompletableFuture[0])).whenComplete((v, e) ->
                    onGlobal(() -> {
                        persist(s);                        // authoritative state; all viewers now closed
                        sessions.remove(key, s);
                        done.complete(null);
                    }));
        });
        return done;
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) return false;
        }
        return true;
    }

    // ---- shutdown ----

    /**
     * Persists every still-open shared session before shutdown. Runs on the disable thread (main /
     * global) with the server stopping, so no viewer can be editing concurrently — encoding the live
     * contents is safe. Each persist registers a pending save that {@link #flushPendingSaves} then waits
     * on.
     */
    private void persistOpenSessions() {
        for (Session s : sessions.values()) {
            if (s.ready && !s.closing) persist(s);
        }
        sessions.clear();
    }

    private void flushPendingSaves() {
        if (pendingSaves.isEmpty()) return;
        try {
            CompletableFuture.allOf(pendingSaves.values().toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Timed out waiting for pending DB saves on shutdown — some data may be lost", e);
        }
    }

    /**
     * Flushes all live sessions and waits for their writes before the plugin disables. Does <b>not</b>
     * close the async executor — that pool is owned by {@link DbExecutor} and shut down separately,
     * after this returns, so the flush above can still dispatch onto it.
     */
    public void shutdown() {
        persistOpenSessions();
        flushPendingSaves();
    }
}

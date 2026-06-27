package com.enhancedechest.service;

import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.serialization.CodecException;
import com.enhancedechest.serialization.ContainerCodec;
import com.enhancedechest.service.ChestSessionManager.ChestRef;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Moves a player's ender chests onto another account ({@code /ee transfer}) for the "I switched account"
 * case. Only NORMAL chests are transferred (TEMP overflow is transient; PERM chests are re-granted by the
 * destination's own permissions), and the transfer is a <b>move</b>: the destination ends up with exactly
 * the source's chests and the source's copies are removed, so no items are ever duplicated.
 *
 * <p>The actual row swap is a single transaction in {@link EnderChestStorage#transferChests}; this class
 * is the orchestration on top of it: it resolves the target, decides whether a conflict flag is required,
 * force-closes every open chest of both players (flushing live edits), and serialises the transaction
 * through {@link ChestSessionManager#runExclusiveAcross} so it is dupe-safe even if a chest was open.
 */
public final class ChestTransferService {

    /** What to do when the destination already holds items in chests this transfer would replace. */
    public enum ConflictPolicy {
        /** No flag given — abort and ask the admin to choose, rather than touch the destination's items. */
        ASK,
        /** Discard the destination's existing items. */
        OVERRIDE,
        /** Move the destination's existing items into recoverable temporary storage. */
        TEMP
    }

    private final ChestSessionManager sessions;
    private final EnderChestStorage storage;
    private final ContainerCodec codec;
    private final StorageGateway storageGateway;
    private final LanguageManager lang;
    private final FoliaLib foliaLib;
    private final DbExecutor db;
    private final Logger logger;

    // Runtime-tunable via /ee reload (mirrors ChestSpillService): lifetime of a temp chest created to
    // preserve the destination's displaced items. volatile so a reload on the main thread is visible.
    private volatile long tempExpiryMillis;

    public ChestTransferService(ChestSessionManager sessions, EnderChestStorage storage,
                                ContainerCodec codec, StorageGateway storageGateway,
                                LanguageManager lang, FoliaLib foliaLib, DbExecutor db, Logger logger,
                                long tempExpiryMillis) {
        this.sessions         = sessions;
        this.storage          = storage;
        this.codec            = codec;
        this.storageGateway   = storageGateway;
        this.lang             = lang;
        this.foliaLib         = foliaLib;
        this.db               = db;
        this.logger           = logger;
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /** Re-applies the runtime-tunable temp-chest lifetime after a {@code /ee reload}. */
    public void setTempExpiry(long tempExpiryMillis) {
        this.tempExpiryMillis = tempExpiryMillis;
    }

    /**
     * Runs a transfer and reports the outcome to {@code sender}. {@code target} is {@code all}, a chest
     * index ({@code 2} or {@code #2}) or a custom chest name belonging to {@code from}.
     */
    public void transfer(CommandSender sender, String fromName, UUID from,
                         String toName, UUID to, String target, ConflictPolicy policy) {
        if (from.equals(to)) {
            sender.sendMessage(lang.get("admin.transfer-same-player"));
            return;
        }

        storageGateway.listChestsAsync(from)
                .thenCompose(fromChests -> storageGateway.listChestsAsync(to)
                        .thenAccept(toChests ->
                                route(sender, fromName, from, toName, to, target, policy, fromChests, toChests)))
                .exceptionally(e -> fail(sender, from, to, e));
    }

    private void route(CommandSender sender, String fromName, UUID from, String toName, UUID to,
                       String target, ConflictPolicy policy,
                       List<ChestSummary> fromChests, List<ChestSummary> toChests) {
        boolean all = target.equalsIgnoreCase("all");
        Integer onlyIndex = all ? null : resolveIndex(target, fromChests);
        if (!all && onlyIndex == null) {
            sender.sendMessage(lang.get("admin.transfer-not-found", "player", fromName, "target", target));
            return;
        }

        final Integer idx = onlyIndex;
        // Source NORMAL chests in scope — these are what gets moved.
        List<ChestSummary> srcScope = fromChests.stream()
                .filter(c -> c.kind() == ChestKind.NORMAL && (all || c.index() == idx))
                .toList();
        if (srcScope.isEmpty()) {
            sender.sendMessage(lang.get(all ? "admin.transfer-no-source" : "admin.transfer-not-found",
                    "player", fromName, "target", target));
            return;
        }

        // Destination NORMAL chests this transfer would replace.
        List<ChestSummary> destScope = toChests.stream()
                .filter(c -> c.kind() == ChestKind.NORMAL && (all || c.index() == idx))
                .toList();

        // Detecting "has items" needs a load + decode, so it runs on the DB executor.
        db.supply(() -> indicesHoldingItems(to, destScope)).thenAccept(itemIndices -> {
            boolean conflict = !itemIndices.isEmpty();
            if (conflict && policy == ConflictPolicy.ASK) {
                sender.sendMessage(lang.get("admin.transfer-needs-flag", "player", toName));
                return;
            }
            Set<Integer> preserve = policy == ConflictPolicy.TEMP ? itemIndices : Set.of();

            // Every open chest of both players is force-closed first so no live session re-saves over the
            // swap; the transaction then runs serialised behind those saves.
            List<ChestRef> refs = collectRefs(from, fromChests, to, toChests);
            forceCloseAll(refs)
                    .thenCompose(v -> sessions.runExclusiveAcross(refs, () ->
                            storage.transferChests(from, to, idx, preserve,
                                    System.currentTimeMillis() + tempExpiryMillis)))
                    .thenAccept(count -> report(sender, fromName, toName, all, count, conflict, policy))
                    .exceptionally(e -> fail(sender, from, to, e));
        }).exceptionally(e -> fail(sender, from, to, e));
    }

    /** Resolves a {@code #N} / bare-int index, or a custom name, to one of {@code from}'s NORMAL chest indices. */
    private @Nullable Integer resolveIndex(String target, List<ChestSummary> fromChests) {
        String trimmed = target.trim();
        String digits = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        if (!digits.isEmpty() && digits.chars().allMatch(Character::isDigit)) {
            int value;
            try {
                value = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return null;
            }
            return fromChests.stream()
                    .anyMatch(c -> c.index() == value && c.kind() == ChestKind.NORMAL) ? value : null;
        }
        return fromChests.stream()
                .filter(c -> c.kind() == ChestKind.NORMAL
                        && c.customName() != null && c.customName().equalsIgnoreCase(trimmed))
                .map(ChestSummary::index)
                .findFirst()
                .orElse(null);
    }

    /** Of the given destination chests, the indices that actually hold at least one item (decoded). */
    private Set<Integer> indicesHoldingItems(UUID owner, List<ChestSummary> destScope) {
        Set<Integer> result = new HashSet<>();
        for (ChestSummary c : destScope) {
            EnderChestData data = storage.loadChest(owner, c.index());
            if (data != null && hasItems(data)) {
                result.add(c.index());
            }
        }
        return result;
    }

    private boolean hasItems(EnderChestData data) {
        byte[] bytes = data.containerData();
        if (bytes == null || bytes.length == 0) return false;
        try {
            ItemStack[] items = codec.decode(bytes, ContainerCodec.MAX_SIZE);
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) return true;
            }
            return false;
        } catch (CodecException e) {
            // Treat undecodable data as "has items" so we never silently discard it without a flag.
            return true;
        }
    }

    private List<ChestRef> collectRefs(UUID from, List<ChestSummary> fromChests,
                                       UUID to, List<ChestSummary> toChests) {
        List<ChestRef> refs = new ArrayList<>();
        for (ChestSummary c : fromChests) refs.add(new ChestRef(from, c.index()));
        for (ChestSummary c : toChests) refs.add(new ChestRef(to, c.index()));
        return refs;
    }

    /** Force-closes every ref in turn, so all live sessions are flushed before the transaction runs. */
    private CompletableFuture<Void> forceCloseAll(List<ChestRef> refs) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (ChestRef ref : refs) {
            chain = chain.thenCompose(v -> sessions.forceCloseAll(ref.owner(), ref.index()));
        }
        return chain;
    }

    private void report(CommandSender sender, String fromName, String toName, boolean all,
                        int count, boolean conflict, ConflictPolicy policy) {
        foliaLib.getScheduler().runNextTick(t -> {
            String base = all ? "admin.transfer-complete-all" : "admin.transfer-complete-one";
            sender.sendMessage(lang.get(base,
                    "from", fromName, "to", toName, "count", Integer.toString(count)));
            if (conflict && policy == ConflictPolicy.TEMP) {
                sender.sendMessage(lang.get("admin.transfer-displaced-temp", "player", toName));
            } else if (conflict && policy == ConflictPolicy.OVERRIDE) {
                sender.sendMessage(lang.get("admin.transfer-displaced-override", "player", toName));
            }
        });
    }

    private Void fail(CommandSender sender, UUID from, UUID to, Throwable e) {
        logger.error("Failed to transfer chests from {} to {}", from, to,
                e.getCause() != null ? e.getCause() : e);
        foliaLib.getScheduler().runNextTick(t -> sender.sendMessage(lang.get("admin.transfer-failed")));
        return null;
    }
}

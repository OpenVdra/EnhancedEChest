package com.enhancedechest.service;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.storage.EnderChestStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Thin async wrapper over {@link EnderChestStorage}: every method dispatches a single synchronous
 * storage call onto the shared {@link DbExecutor}. Pure delegation — no session/dupe-safety logic
 * lives here (that is {@link ChestSessionManager}); these are the fire-and-report operations used by
 * the dialog callbacks, admin commands, and open routing.
 */
public final class StorageGateway {

    private final EnderChestStorage storage;
    private final DbExecutor db;

    public StorageGateway(EnderChestStorage storage, DbExecutor db) {
        this.storage = storage;
        this.db = db;
    }

    public CompletableFuture<List<ChestSummary>> listChestsAsync(UUID owner) {
        return db.supply(() -> storage.listChests(owner));
    }

    /** Creates a NORMAL chest, expirable if {@code expiresAt} is non-null (epoch millis). */
    public CompletableFuture<Integer> createChestAsync(UUID owner, int size, @Nullable Long expiresAt) {
        return db.supply(() -> storage.createChest(owner, size, expiresAt));
    }

    public CompletableFuture<Void> renameAsync(UUID owner, int index, @Nullable String name) {
        return db.run(() -> storage.renameChest(owner, index, name));
    }

    /** Sets or clears a chest's icon (material key, or null to reset to the default icon). */
    public CompletableFuture<Void> setIconAsync(UUID owner, int index, @Nullable String icon) {
        return db.run(() -> storage.setIcon(owner, index, icon));
    }

    public CompletableFuture<Void> setPrimaryAsync(UUID owner, int index) {
        return db.run(() -> storage.setPrimary(owner, index));
    }

    public CompletableFuture<Void> clearPrimaryAsync(UUID owner) {
        return db.run(() -> storage.clearPrimary(owner));
    }
}

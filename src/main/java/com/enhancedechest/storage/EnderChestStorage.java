package com.enhancedechest.storage;

import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.model.PlayerSettings;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * All methods execute synchronously on the calling thread.
 * No background threads, no queued writes — callers (the {@code com.enhancedechest.service} layer,
 * via {@code DbExecutor}) are responsible for dispatching these onto an async executor and never
 * blocking a region/main thread.
 *
 * <p>Ownership model: a player owns a chest iff a row exists for (player_uuid, chest_index).
 * Chests are created explicitly (admin command, API, or the auto-bootstrap of the first chest).
 */
public interface EnderChestStorage {

    /** Creates schema and prepares connections. Must be called once before any other method. */
    void init();

    /** Closes all connections. Safe to call even if init() was not completed. */
    void close();

    /**
     * True if this backend can produce a file snapshot via {@link #backup(Path)}. Only the
     * file-based SQLite backend supports it today; remote backends (MySQL/MariaDB/Postgres) return
     * false and must be backed up with the database server's own tooling.
     */
    default boolean supportsBackup() {
        return false;
    }

    /**
     * Writes a consistent snapshot of the entire database to {@code target} (which must not already
     * exist). The snapshot is safe to take while players are saving — it does not interrupt writes.
     * Only valid when {@link #supportsBackup()} is true.
     *
     * @throws Exception if the snapshot fails; the caller logs it and leaves the live DB untouched.
     */
    default void backup(Path target) throws Exception {
        throw new UnsupportedOperationException("Backup is not supported by this storage backend");
    }

    /** Returns the player's chests ordered by index. Empty list if the player owns none. */
    List<ChestSummary> listChests(UUID owner);

    /**
     * Returns the index of the chest /ec opens: the primary if one is flagged, otherwise the
     * lowest-indexed chest. Returns -1 if the player owns no chests.
     */
    int getPrimaryIndex(UUID owner);

    /** Loads a single chest, or null if no such (owner, index) row exists. */
    @Nullable EnderChestData loadChest(UUID owner, int index);

    /**
     * Updates the container bytes of an existing chest. No-op if the row was deleted
     * while open. Size, name and primary flag are never touched here.
     */
    void saveChest(UUID owner, int index, byte[] containerData);

    /**
     * Creates a new permanent chest with the next free index (max+1, or 1 if the player has none).
     * No chest is ever auto-flagged primary; the main chest is set only via {@link #setPrimary}.
     *
     * @return the index assigned to the new chest
     */
    default int createChest(UUID owner, int size) {
        return createChest(owner, size, null);
    }

    /**
     * Creates a new NORMAL chest with the next free index. If {@code expiresAt} is non-null the
     * chest expires at that epoch-millis instant (an expirable granted chest); null = never expires.
     * No chest is ever auto-flagged primary; the main chest is set only via {@link #setPrimary}.
     *
     * @return the index assigned to the new chest
     */
    int createChest(UUID owner, int size, @Nullable Long expiresAt);

    /**
     * Creates a permission-granted chest (kind=PERM) with the next free index. PERM chests carry no
     * expiry and are never auto-flagged primary; they are granted/removed by the reconcile of
     * {@code com.enhancedechest.service.PermissionChestService} and are invisible to admin commands.
     *
     * @return the index assigned to the new chest
     */
    int createPermChest(UUID owner, int size);

    /**
     * Shrinks a chest and spills any cut-off items into a new temp chest, in one transaction.
     * The original row is updated to {@code newSize} with {@code visible} as its new contents; if
     * {@code overflow} is non-null a temp chest (kind=TEMP, next free index, expiring at
     * {@code tempExpiresAt}) is inserted holding it. Never leaves items in two rows at once.
     *
     * @param tempSize slot count of the temp chest created for the overflow (ignored if no overflow)
     */
    void spillShrink(UUID owner, int index, int newSize, byte[] visible,
                     @Nullable byte[] overflow, int tempSize, long tempExpiresAt);

    /**
     * Deletes a chest, optionally spilling its items into a new temp chest, in one transaction.
     * If {@code items} is non-null a temp chest holding them is inserted before the original row is
     * deleted. No survivor is promoted to primary (the main is an explicit player choice).
     *
     * @param tempSize slot count of the temp chest created for the items (ignored if {@code items} is null)
     */
    void spillRemove(UUID owner, int index, @Nullable byte[] items, int tempSize, long tempExpiresAt);

    /** Returns every chest whose expiry is set and at or before {@code now}. */
    List<ExpiredRef> findExpired(long now);

    /** Lightweight reference to an expired chest, returned by {@link #findExpired(long)}. */
    record ExpiredRef(UUID owner, int index, com.enhancedechest.model.ChestKind kind) {}

    /**
     * Creates a chest at a specific index if it does not already exist (used by migration).
     * No-op if the row already exists. Never auto-flags primary.
     */
    void ensureChest(UUID owner, int index, int size);

    /** Changes a chest's slot count. Caller validates size (multiple of 9, 9..54). */
    void resizeChest(UUID owner, int index, int size);

    /**
     * Deletes a chest. No survivor is promoted: if the deleted chest was the main, the player
     * simply has no main until they choose one again.
     */
    void deleteChest(UUID owner, int index);

    /** Empties a chest's contents (sets the stored bytes to NULL), keeping its size, name, icon and kind. */
    void clearChestContents(UUID owner, int index);

    /**
     * Moves a player's NORMAL chests onto another player in a single transaction (the account-switch
     * primitive behind {@code /ee transfer}). For each source NORMAL chest (all of them, or only
     * {@code onlyIndex} when non-null) a copy is written to {@code to} at the <i>same</i> index,
     * carrying its size, custom name, icon and contents (and, on a full transfer, the primary flag);
     * the source row is then deleted, so the items live on exactly one account.
     *
     * <p>The destination's pre-existing NORMAL chests in scope are removed first so the destination
     * ends up with exactly the source's chest count and nothing stacked on top. Any destination index
     * listed in {@code preserveDestIndices} is re-inserted as a TEMP chest (carrying its items and
     * expiring at {@code tempExpiresAt}) before removal, so those items remain recoverable; indices not
     * listed are discarded. Destination PERM/TEMP chests sitting on an index the source needs are
     * relocated to a free index rather than dropped. The whole thing is one transaction, so a failure
     * leaves both players untouched.
     *
     * @param onlyIndex           a single source index to transfer, or null for every NORMAL chest
     * @param preserveDestIndices destination indices whose items must be kept (spilled to a temp chest);
     *                            empty to discard every replaced destination chest
     * @param tempExpiresAt       epoch-millis expiry stamped on any temp chest created for preserved items
     * @return the number of chests transferred
     */
    int transferChests(UUID from, UUID to, @Nullable Integer onlyIndex,
                       java.util.Set<Integer> preserveDestIndices, long tempExpiresAt);

    /** Sets or clears a chest's custom display name (null resets to the default numbered title). */
    void renameChest(UUID owner, int index, @Nullable String name);

    /** Sets or clears a chest's icon (material key, e.g. {@code minecraft:diamond}; null resets to default). */
    void setIcon(UUID owner, int index, @Nullable String icon);

    /** Makes the given chest the player's primary, clearing the flag from all others. */
    void setPrimary(UUID owner, int index);

    /** Clears the primary flag from all of the player's chests, leaving them with no main chest. */
    void clearPrimary(UUID owner);

    /** Returns true if the player's chest #1 has its migrated flag set. */
    boolean isMigrated(UUID owner);

    /** Updates the migrated flag on the player's chest #1. No-op if chest #1 does not exist. */
    void setMigrated(UUID owner, boolean migrated);

    /**
     * Loads the player's settings, or {@link PlayerSettings#defaults()} if they have no row yet.
     * Never returns null — an absent row is indistinguishable from an all-defaults one.
     */
    PlayerSettings loadSettings(UUID owner);

    /** Upserts the player's settings (whole object): updates the existing row, or inserts one if none exists. */
    void saveSettings(UUID owner, PlayerSettings settings);

    /**
     * Targeted single-field upsert of just the edit-mode preference. Cheaper than
     * {@link #loadSettings} + {@link #saveSettings} for a one-field toggle (no preceding read) and
     * never clobbers other settings. Use {@link #saveSettings} when persisting the whole object.
     */
    void setEditMode(UUID owner, boolean editMode);
}

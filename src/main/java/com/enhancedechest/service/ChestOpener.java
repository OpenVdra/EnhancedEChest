package com.enhancedechest.service;

import com.enhancedechest.gui.EnderChestHolder;
import com.enhancedechest.gui.dialog.ChestDialogs;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

/**
 * Decides <i>what</i> to open for {@code /ec}, {@code /eclist}, right-click and the admin {@code /ee
 * view}, and orchestrates the management dialogs. The actual attach-to-shared-session work is delegated
 * to {@link ChestSessionManager#open} (the single dupe-safe funnel); this class is the GUI-flow layer
 * on top of it.
 *
 * <p>Open routing for {@code /ec} / right-click ({@link #open}):
 * <ul>
 *   <li>0 or 1 normal chest and no temp chest — opens that chest directly (creating chest #1 if the
 *       player owns none);</li>
 *   <li>2+ normal chests, no temp chest, an explicit main is set <i>and</i> the player may use it —
 *       opens the main directly;</li>
 *   <li>otherwise — opens the management list dialog so the player picks (or sets a main).</li>
 * </ul>
 * A main is never auto-assigned at creation, so a multi-chest player who has not chosen one always
 * lands on the management dialog. {@code /eclist} always reaches the dialog regardless.
 */
public final class ChestOpener {

    /** Permission to open the ender chest by command; also gates the dialog's "set as main" action. */
    private static final String OPEN_GUI_PERMISSION = "enhancedechest.command.open";

    private final ChestSessionManager sessions;
    private final StorageGateway storageGateway;
    private final PlayerSettingsCache settings;
    private final EnderChestStorage storage;
    private final DbExecutor db;
    private final LanguageManager lang;
    private final FoliaLib foliaLib;
    private final Logger logger;
    private final ChestDialogs dialogs;

    // Runtime-tunable via /ee reload (see setDefaultSize). volatile so the value written on the main
    // thread during a reload is visible to the async open threads that read it when bootstrapping.
    /** Size of the chest auto-created the first time a player ever opens their ender chest. */
    private volatile int defaultSize;

    public ChestOpener(ChestSessionManager sessions, StorageGateway storageGateway,
                       PlayerSettingsCache settings, EnderChestStorage storage, DbExecutor db,
                       LanguageManager lang, FoliaLib foliaLib, Logger logger, int defaultSize) {
        this.sessions       = sessions;
        this.storageGateway = storageGateway;
        this.settings       = settings;
        this.storage        = storage;
        this.db             = db;
        this.lang           = lang;
        this.foliaLib       = foliaLib;
        this.logger         = logger;
        this.defaultSize    = defaultSize;
        this.dialogs        = new ChestDialogs(this, storageGateway, settings, lang);
    }

    /**
     * Re-applies the runtime-tunable default chest size after a {@code /ee reload}. Read only when
     * bootstrapping a brand-new chest, so it is dupe-safe to set on the main thread while async storage
     * work is pending.
     */
    public void setDefaultSize(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    // ---- opening ----

    /**
     * Default open entry point for {@code /enderchest} and right-click:
     * <ul>
     *   <li>0 or 1 normal chest and no temp chest — opens that chest directly (creating chest #1 if
     *       the player owns none);</li>
     *   <li>2+ normal chests, no temp chest, an explicit main is set <i>and</i> the player may use it
     *       — opens the main directly;</li>
     *   <li>otherwise — opens the management list dialog so the player picks (or sets a main).</li>
     * </ul>
     *
     * <p>A main is never auto-assigned at creation, so a multi-chest player who has not chosen one
     * always lands on the management dialog. Setting a main returns them to the open-directly path.
     * Players without the open-by-command permission can never have an effective main, so with 2+
     * chests they always get the dialog. Any TEMP (overflow) chest also forces the dialog, since
     * spilled items can only be retrieved from the list. {@code /eclist} still reaches the dialog
     * regardless.
     *
     * @param sourceBlock ender chest block location if opened via right-click; null for command/dialog
     */
    public void open(Player player, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        foliaLib.getScheduler().runAtEntity(player, outerTask -> {
            closeExistingGui(player);
            storageGateway.listChestsAsync(uuid)
                    .thenAccept(chests -> {
                        // Spilled items live in TEMP chests that can ONLY be retrieved from the list
                        // dialog, so any temp chest forces the dialog regardless of how many normal
                        // chests exist (otherwise a single-chest player could never reach the overflow).
                        boolean hasTemp = chests.stream().anyMatch(c -> c.kind() == ChestKind.TEMP);
                        long normalCount = chests.stream().filter(c -> c.kind() == ChestKind.NORMAL).count();
                        // 0 or 1 normal chest and nothing spilled: open it directly (bootstrapping
                        // chest #1 if the player owns none).
                        if (!hasTemp && normalCount <= 1) {
                            openPrimaryChest(player, uuid, sourceBlock);
                            return;
                        }
                        // 2+ chests: only an explicitly-flagged main, set by a player who may use it,
                        // bypasses the dialog — and never while a temp chest is awaiting recovery.
                        // Otherwise show the management list.
                        Integer mainIndex = (!hasTemp && canSetMain)
                                ? chests.stream().filter(ChestSummary::primary)
                                        .map(ChestSummary::index).findFirst().orElse(null)
                                : null;
                        if (mainIndex != null) {
                            openChest(player, mainIndex, sourceBlock);
                        } else {
                            // Seed the edit-mode checkbox from the player's saved preference.
                            settings.loadSettingsAsync(uuid).thenAccept(s ->
                                    foliaLib.getScheduler().runAtEntity(player, task -> {
                                        if (player.isOnline()) player.showDialog(
                                                dialogs.listDialog(chests, canSetMain, sourceBlock, s.editMode()));
                                    }));
                        }
                    })
                    .exceptionally(e -> reportOpenFailure(player, e));
        });
    }

    /** Loads and opens the player's primary chest inventory directly (creating chest #1 if none exist). */
    private void openPrimaryChest(Player player, UUID uuid, @Nullable Location sourceBlock) {
        db.supply(() -> resolvePrimaryIndex(uuid))
                .thenAccept(index -> sessions.open(player, uuid, index, sourceBlock))
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
        storageGateway.listChestsAsync(uuid).thenAccept(chests ->
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

    /** Opens a specific chest by index (from the management dialog), sharing the live session. */
    public void openChest(Player player, int index, @Nullable Location sourceBlock) {
        sessions.open(player, player.getUniqueId(), index, sourceBlock);
    }

    /**
     * Opens another player's chest for an admin, sharing the live session. The admin becomes a viewer of
     * the same inventory the owner sees (concurrent edit on Paper; exclusive on Folia). Read-only vs
     * editable is enforced per-click in the GUI listener via the admin's permissions, so this method
     * itself simply joins the session.
     */
    public void adminOpen(Player admin, UUID owner, int index) {
        sessions.open(admin, owner, index, null);
    }

    private int resolvePrimaryIndex(UUID uuid) {
        int index = storage.getPrimaryIndex(uuid);
        if (index == -1) {
            // First-ever access: bootstrap chest #1. It is NOT flagged primary — with a single chest,
            // getPrimaryIndex falls back to the lowest index, so /ec still opens it directly.
            index = storage.createChest(uuid, defaultSize);
        }
        return index;
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

    /** Loads the player's chests and shows the /eclist management dialog, seeding edit mode from their saved preference. */
    public void openListDialog(Player player) {
        settings.loadSettingsAsync(player.getUniqueId())
                .thenAccept(s -> openListDialog(player, s.editMode(), null))
                .exceptionally(e -> reportOpenFailure(player, e));
    }

    /**
     * Loads the player's chests and shows the management dialog with the edit-mode checkbox in the
     * given starting state. Fresh opens seed it from the player's saved preference (see the no-arg
     * overload and {@link #open}); returning from a detail dialog's Back seeds it on so the player
     * stays in edit mode. The checkbox itself toggles client-side without re-showing.
     *
     * @param editInitial starting state of the dialog's edit-mode checkbox
     * @param sourceBlock ender chest block this menu was opened from (threaded through so direct opens
     *                    still animate), or null when opened by command
     */
    public void openListDialog(Player player, boolean editInitial, @Nullable Location sourceBlock) {
        UUID uuid = player.getUniqueId();
        boolean canSetMain = canSetMain(player);
        storageGateway.listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    if (chests.isEmpty()) {
                        player.sendMessage(lang.get("chest.none"));
                        return;
                    }
                    player.showDialog(dialogs.listDialog(chests, canSetMain, sourceBlock, editInitial));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    /**
     * Shows the admin "view another player's chests" list dialog. Each button opens the target's chest
     * for the admin via the shared session ({@link #adminOpen}) — no edit-mode/rename/set-main. The
     * chests are passed in already loaded (the command lists them to route 0/1/2+), so this only builds
     * and pushes the dialog on the admin's entity thread.
     */
    public void showAdminViewList(Player admin, String targetName, UUID target, List<ChestSummary> chests) {
        foliaLib.getScheduler().runAtEntity(admin, t -> {
            if (admin.isOnline()) admin.showDialog(dialogs.adminViewListDialog(targetName, target, chests));
        });
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
        storageGateway.listChestsAsync(uuid).thenAccept(chests ->
                foliaLib.getScheduler().runAtEntity(player, task -> {
                    if (!player.isOnline()) return;
                    chests.stream().filter(c -> c.index() == index).findFirst().ifPresentOrElse(
                            c -> player.showDialog(builder.apply(c)),
                            () -> player.sendMessage(lang.get("chest.not-found")));
                })
        ).exceptionally(e -> reportOpenFailure(player, e));
    }

    /** Runs the given action on the player's entity thread (helper for command/dialog callbacks). */
    public void runForPlayer(Player player, Runnable action) {
        foliaLib.getScheduler().runAtEntity(player, task -> action.run());
    }
}

package com.enhancedechest.gui.dialog;

import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.lang.LanguageManager;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.util.DurationFormat;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Builds the /ec list management dialogs using Paper's (experimental) Dialog API.
 *
 * <p>All Dialog API usage is isolated here so a Paper breaking change requires edits in one place
 * only (mirrors how {@code ContainerCodec} isolates the Data Component API).
 *
 * <p>Three levels: list (one button per chest), per-chest detail (Open / Rename / Set-as-main),
 * and a dedicated rename dialog (text input + Save / Cancel).
 *
 * <p>Navigation strategy — to avoid the cursor recentre that a server-pushed {@code showDialog}
 * causes, <i>forward</i> navigation (list→detail, detail→rename) uses a client-side
 * {@code show_dialog} action ({@link ClickEvent#showDialog}); the client swaps the dialog in place
 * without reopening the screen. Back/Cancel and post-mutation refreshes re-query the DB and are
 * pushed from the server (these legitimately recentre, since the underlying data may have changed).
 */
@SuppressWarnings("UnstableApiUsage")
public final class ChestDialogs {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int BUTTON_WIDTH = 180;
    private static final int BODY_WIDTH = 200;

    // Icon picker grid: a single scrollable multi-action grid (no paging, so browsing never re-pushes
    // the dialog and never recentres the cursor). Search narrows the list when the catalog is large.
    private static final int ICON_COLUMNS = 4;
    private static final int ICON_BUTTON_WIDTH = 150;
    private static final int ICON_SEARCH_MAX_LENGTH = 48;

    /** Key of the icon picker's search text input, read at click time to filter the catalog. */
    private static final String ICON_SEARCH_INPUT = "icon_search";

    /** Key of the list dialog's edit-mode checkbox, read at click time to route chest buttons. */
    private static final String EDIT_MODE_INPUT = "edit_mode";

    private final EnderChestService service;
    private final LanguageManager lang;

    public ChestDialogs(EnderChestService service, LanguageManager lang) {
        this.service = service;
        this.lang = lang;
    }

    /**
     * Top-level list: one button per chest, plus an in-dialog "edit mode" checkbox.
     *
     * <p>The edit-mode checkbox decides what clicking a chest does, read fresh at click time:
     * <ul>
     *   <li><b>off</b> (default) — clicking a chest opens its inventory directly;</li>
     *   <li><b>on</b> — clicking a chest opens its management detail dialog (rename, set main, …).</li>
     * </ul>
     * Because the mode lives in a {@linkplain DialogInput#bool boolean input} the client toggles
     * locally, flipping it never reopens the dialog — so the cursor never recentres (a server-pushed
     * rebuild would have). {@code editInitial} only seeds the checkbox's starting state, used to keep
     * edit mode on when returning from a detail dialog's Back.
     *
     * @param canSetMain  whether the viewer may set a chest as their main (gated on the
     *                    open-by-command permission); threaded into each detail dialog
     * @param sourceBlock ender chest block this menu was opened from (for the lid close animation),
     *                    or null when opened by command; threaded into each chest's open/detail
     * @param editInitial starting state of the edit-mode checkbox (false for a fresh open)
     */
    public Dialog listDialog(List<ChestSummary> chests, boolean canSetMain,
                             @Nullable Location sourceBlock, boolean editInitial) {
        // Temporary chests always sort to the top so players notice them (they expire); within each
        // group the natural index order is preserved.
        List<ChestSummary> ordered = new ArrayList<>(chests);
        ordered.sort(Comparator
                .comparingInt((ChestSummary c) -> c.kind() == ChestKind.TEMP ? 0 : 1)
                .thenComparingInt(ChestSummary::index));

        List<ActionButton> buttons = new ArrayList<>(ordered.size());
        for (ChestSummary chest : ordered) {
            int index = chest.index();
            Component label = withIcon(chest, lang.getChestLabel(index, chest.customName(), chest.kind()));
            if (chest.primary()) {
                label = label.append(Component.text(" ")).append(lang.getGui("dialog.main-tag"));
            }
            // Branch on the edit-mode checkbox at click time. Edit off: open the chest directly
            // (opening an inventory dismisses the dialog, so its recentre is moot). Edit on: push the
            // detail dialog from the player's thread, source block preserved for its Open animation.
            buttons.add(ActionButton.create(label, listTooltip(chest), BUTTON_WIDTH,
                    click((view, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        if (Boolean.TRUE.equals(view.getBoolean(EDIT_MODE_INPUT))) {
                            service.runForPlayer(p, () -> {
                                if (p.isOnline()) p.showDialog(detailDialog(chest, canSetMain, sourceBlock));
                            });
                        } else {
                            service.openChest(p, index, sourceBlock);
                        }
                    })));
        }

        // Dedicated close button (the dialog's exit action) — no action set means clicking it just
        // dismisses the menu.
        ActionButton close = ActionButton.builder(lang.getGui("dialog.close"))
                .width(BUTTON_WIDTH)
                .build();

        // In-dialog toggle: a checkbox the client flips locally, so switching modes never reopens the
        // dialog (and so never recentres the cursor). Rendered in the body, above the chest buttons.
        DialogInput editMode = DialogInput.bool(EDIT_MODE_INPUT, lang.getGui("dialog.edit-mode"))
                .initial(editInitial)
                .build();

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.list-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.list-body"), BODY_WIDTH)))
                        .inputs(List.of(editMode))
                        .build())
                .type(DialogType.multiAction(buttons, close, columnsFor(ordered.size()))));
    }

    /**
     * Chest-list grid width: keep a single familiar column for short lists, but fan wider lists out
     * into multiple columns so they use the horizontal space instead of forcing a tall scrollbar.
     */
    private static int columnsFor(int count) {
        if (count <= 7) return 1;
        if (count <= 14) return 2;
        return 3;
    }

    /**
     * Per-chest detail: Open / Set-as-main / Rename / Choose icon / Back.
     *
     * @param canSetMain  whether to show the "set as main" button; hidden for viewers without the
     *                    open-by-command permission, for whom a main chest is meaningless
     * @param sourceBlock ender chest block this menu was opened from, or null when opened by command;
     *                    passed to the inventory open so the lid close animation fires on close
     */
    public Dialog detailDialog(ChestSummary chest, boolean canSetMain, @Nullable Location sourceBlock) {
        int index = chest.index();
        boolean temp = chest.kind() == ChestKind.TEMP;
        List<ActionButton> buttons = new ArrayList<>(4);

        // Open first — the primary action, and the most common reason to be here.
        buttons.add(ActionButton.create(lang.getGui("dialog.open"), lang.getGui("dialog.open-desc"), BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openChest(p, index, sourceBlock);
                })));

        // Temp chests are transient overflow holders: no main flag, no customisation. Only Open + Back.
        if (!temp) {
            // Set as main / Unset main — the highest-impact toggle (changes what /ec opens), so it sits
            // right after Open. Mutates data, so it re-queries and is re-pushed from the server.
            if (canSetMain && !chest.primary()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.set-main"), lang.getGui("dialog.set-main-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            service.setPrimaryAsync(p.getUniqueId(), index)
                                    .thenRun(() -> service.openDetailDialog(p, index));
                        })));
            } else if (canSetMain && chest.primary()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.unset-main"), lang.getGui("dialog.unset-main-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            service.clearPrimaryAsync(p.getUniqueId())
                                    .thenRun(() -> service.openDetailDialog(p, index));
                        })));
            }

            // Appearance edits grouped together: Rename then Choose icon. Both forward in-place
            // (client-side show_dialog), so they never recentre the cursor. Choose icon's picker has a
            // "Default" button to drop back to the plain ender-chest icon, so no separate clear button.
            buttons.add(ActionButton.create(lang.getGui("dialog.rename"), lang.getGui("dialog.rename-desc"), BUTTON_WIDTH,
                    DialogAction.staticAction(ClickEvent.showDialog(renameDialog(chest)))));
            buttons.add(ActionButton.create(lang.getGui("dialog.choose-icon"), lang.getGui("dialog.choose-icon-desc"),
                    BUTTON_WIDTH, DialogAction.staticAction(ClickEvent.showDialog(iconPickerDialog(chest, "")))));
        }

        // Back returns to the list in edit mode — the detail dialog is only reachable from there.
        buttons.add(ActionButton.create(lang.getGui("dialog.back"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openListDialog(p, true, sourceBlock);
                })));

        Component title = withIcon(chest, lang.getChestLabel(index, chest.customName(), chest.kind()));
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(detailBody(chest)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    /**
     * Icon picker: a single, searchable, <b>scrollable</b> grid of every matching material rendered with
     * its in-game sprite (via {@link IconCatalog}). The grid is intentionally <i>not</i> paged — the
     * client scrolls the button list, so browsing icons never re-pushes the dialog from the server and
     * therefore never recentres the cursor (paging buttons would, since their target page can only be
     * server-pushed). Search narrows the list when the full catalog is unwieldy.
     *
     * <p>Layout: the control row (Search / Default) is the first buttons, the icons follow, and Back is
     * the dialog's exit action (kept reachable while scrolling). Search reads the text input and
     * re-shows the filtered list. Picking an icon, or Default, writes the change and returns to detail.
     *
     * @param filter case-insensitive name filter ("" = whole catalog)
     */
    public Dialog iconPickerDialog(ChestSummary chest, String filter) {
        int index = chest.index();
        List<IconCatalog.Entry> results = IconCatalog.search(filter);

        List<ActionButton> buttons = new ArrayList<>(results.size() + 2);

        // Search: re-show the list filtered by the freshly typed text (an explicit, server-pushed action).
        buttons.add(ActionButton.create(lang.getGui("dialog.icon-search"), null, ICON_BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText(ICON_SEARCH_INPUT);
                    String query = typed == null ? "" : typed.trim();
                    service.runForPlayer(p, () -> {
                        if (p.isOnline()) p.showDialog(iconPickerDialog(chest, query));
                    });
                })));
        // Default: clear the icon back to the ender-chest default.
        buttons.add(ActionButton.create(lang.getGui("dialog.icon-default"), lang.getGui("dialog.icon-default-desc"),
                ICON_BUTTON_WIDTH, click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    service.setIconAsync(p.getUniqueId(), index, null)
                            .thenRun(() -> service.openDetailDialog(p, index));
                })));

        // One button per matching icon; the client scrolls the grid.
        for (IconCatalog.Entry entry : results) {
            Component label = entry.sprite().append(Component.text(" "))
                    .append(Component.text(entry.displayName()));
            buttons.add(ActionButton.create(label, null, ICON_BUTTON_WIDTH,
                    click((view, audience) -> {
                        if (!(audience instanceof Player p)) return;
                        service.setIconAsync(p.getUniqueId(), index, entry.key())
                                .thenRun(() -> service.openDetailDialog(p, index));
                    })));
        }

        DialogInput search = DialogInput.text(ICON_SEARCH_INPUT, lang.getGui("dialog.icon-search-label"))
                .initial(filter)
                .maxLength(ICON_SEARCH_MAX_LENGTH)
                .build();

        Component body = lang.getGui("dialog.icon-body", "count", Integer.toString(results.size()));

        ActionButton back = ActionButton.create(lang.getGui("dialog.back"), null, ICON_BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openDetailDialog(p, index);
                }));

        Component title = lang.getGui("dialog.icon-title");
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(DialogBody.plainMessage(body, BODY_WIDTH)))
                        .inputs(List.of(search))
                        .build())
                .type(DialogType.multiAction(buttons, back, ICON_COLUMNS)));
    }

    /** Prepends the chest's chosen icon sprite (if any) to a label/title, separated by a space. */
    private Component withIcon(ChestSummary chest, Component label) {
        Component icon = IconCatalog.sprite(chest.icon());
        if (icon == null) {
            return label;
        }
        return icon.append(Component.text(" ")).append(label);
    }

    /** Dedicated rename dialog: a single text input plus Save / Cancel. */
    public Dialog renameDialog(ChestSummary chest) {
        int index = chest.index();
        String current = chest.customName() != null ? chest.customName() : "";

        DialogInput nameInput = DialogInput.text("name", lang.getGui("dialog.name-label"))
                .initial(current)
                .maxLength(MAX_NAME_LENGTH)
                .build();

        ActionButton save = ActionButton.create(lang.getGui("dialog.save-name"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (!(audience instanceof Player p)) return;
                    String typed = view.getText("name");
                    String name = (typed == null || typed.isBlank()) ? null : typed.trim();
                    service.renameAsync(p.getUniqueId(), index, name)
                            .thenRun(() -> service.openDetailDialog(p, index));
                }));

        ActionButton cancel = ActionButton.create(lang.getGui("dialog.cancel"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openDetailDialog(p, index);
                }));

        Component title = lang.getChestLabel(index, chest.customName(), chest.kind());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.rename-body"), BODY_WIDTH)))
                        .inputs(List.of(nameInput))
                        .build())
                .type(DialogType.multiAction(List.of(save, cancel), null, 1)));
    }

    /**
     * Detail-dialog body: a centred slot count, plus the static "expires in" snapshot for expiring
     * chests. The chest's chosen icon is shown in the title (see {@link #withIcon}), so the body stays a
     * clean centred message rather than an item body whose icon floats off to one side.
     */
    private DialogBody detailBody(ChestSummary chest) {
        Component info = lang.getGui("dialog.detail-body", "size", Integer.toString(chest.size()));
        Component expiry = expiryTooltip(chest);
        if (expiry != null) {
            info = info.appendNewline().append(expiry);
        }
        return DialogBody.plainMessage(info, BODY_WIDTH);
    }

    /** List-button tooltip: slot count, plus a static "expires in" snapshot for expiring chests. */
    private Component listTooltip(ChestSummary chest) {
        Component tip = lang.getGui("dialog.slots", "size", Integer.toString(chest.size()));
        Component expiry = expiryTooltip(chest);
        return expiry == null ? tip : tip.appendNewline().append(expiry);
    }

    /**
     * Static "expires in &lt;time&gt;" snapshot recomputed each time the dialog is built (a live
     * ticking countdown is impossible with the static Dialog API). Null for chests that never expire.
     */
    private @Nullable Component expiryTooltip(ChestSummary chest) {
        if (chest.expiresAt() == null) {
            return null;
        }
        String remaining = DurationFormat.formatRemaining(chest.expiresAt() - System.currentTimeMillis());
        return lang.getGui("dialog.expires-in", "time", remaining);
    }

    private static DialogAction click(BiConsumer<io.papermc.paper.dialog.DialogResponseView,
            net.kyori.adventure.audience.Audience> body) {
        return DialogAction.customClick(
                body::accept,
                ClickCallback.Options.builder().build());
    }
}

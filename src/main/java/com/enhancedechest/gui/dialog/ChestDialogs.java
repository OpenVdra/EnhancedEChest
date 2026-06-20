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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    private final EnderChestService service;
    private final LanguageManager lang;

    public ChestDialogs(EnderChestService service, LanguageManager lang) {
        this.service = service;
        this.lang = lang;
    }

    /**
     * Top-level list: one button per chest, clicking opens that chest's detail dialog in place.
     *
     * @param canSetMain  whether the viewer may set a chest as their main (gated on the
     *                    open-by-command permission); threaded into each detail dialog
     * @param sourceBlock ender chest block this menu was opened from (for the lid close animation),
     *                    or null when opened by command; threaded into each detail dialog's Open
     */
    public Dialog listDialog(List<ChestSummary> chests, boolean canSetMain, @Nullable Location sourceBlock) {
        List<ActionButton> buttons = new ArrayList<>(chests.size());
        for (ChestSummary chest : chests) {
            Component label = lang.getChestLabel(chest.index(), chest.customName(), chest.kind());
            if (chest.primary()) {
                label = label.append(Component.text(" ")).append(lang.getGui("dialog.main-tag"));
            }
            // Forward, in-place: open this chest's detail dialog client-side (no cursor reset).
            buttons.add(ActionButton.create(label, listTooltip(chest), BUTTON_WIDTH,
                    DialogAction.staticAction(ClickEvent.showDialog(detailDialog(chest, canSetMain, sourceBlock)))));
        }

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(lang.getGui("dialog.list-title"))
                        .body(List.of(DialogBody.plainMessage(lang.getGui("dialog.list-body"), BODY_WIDTH)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
    }

    /**
     * Per-chest detail: Open / Rename / Set-as-main / Back.
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

        // Open the actual inventory (closes the dialog; cursor position is moot once an inventory opens).
        buttons.add(ActionButton.create(lang.getGui("dialog.open"), lang.getGui("dialog.open-desc"), BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openChest(p, index, sourceBlock);
                })));

        // Temp chests are transient overflow holders: no renaming, no setting as main. Only Open + Back.
        if (!temp) {
            // Forward, in-place: go to the dedicated rename dialog client-side (no cursor reset).
            buttons.add(ActionButton.create(lang.getGui("dialog.rename"), lang.getGui("dialog.rename-desc"), BUTTON_WIDTH,
                    DialogAction.staticAction(ClickEvent.showDialog(renameDialog(chest)))));

            // Set as main — mutates data, so it re-queries and is re-pushed from the server.
            if (canSetMain && !chest.primary()) {
                buttons.add(ActionButton.create(lang.getGui("dialog.set-main"), lang.getGui("dialog.set-main-desc"),
                        BUTTON_WIDTH, click((view, audience) -> {
                            if (!(audience instanceof Player p)) return;
                            service.setPrimaryAsync(p.getUniqueId(), index)
                                    .thenRun(() -> service.openDetailDialog(p, index));
                        })));
            }
        }

        buttons.add(ActionButton.create(lang.getGui("dialog.back"), null, BUTTON_WIDTH,
                click((view, audience) -> {
                    if (audience instanceof Player p) service.openListDialog(p);
                })));

        Component title = lang.getChestLabel(index, chest.customName(), chest.kind());
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(List.of(chestIconBody(chest)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 1)));
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
     * Detail-dialog body: a chest item icon with a centred description (slot count, plus the static
     * "expires in" snapshot for expiring chests). Decorations and the item's own tooltip are hidden
     * so only our description shows.
     */
    private DialogBody chestIconBody(ChestSummary chest) {
        Component info = lang.getGui("dialog.detail-body", "size", Integer.toString(chest.size()));
        Component expiry = expiryTooltip(chest);
        if (expiry != null) {
            info = info.appendNewline().append(expiry);
        }
        return DialogBody.item(ItemStack.of(Material.ENDER_CHEST))
                .description(DialogBody.plainMessage(info, BODY_WIDTH))
                .showDecorations(false)
                .showTooltip(false)
                .build();
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

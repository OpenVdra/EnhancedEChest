package com.enhancedechest.model;

import org.jetbrains.annotations.Nullable;

/**
 * Lightweight description of one of a player's ender chests, used to build the
 * /ec list management dialog and to decide which chest /ec opens.
 *
 * @param index      1-based chest index
 * @param size       slot count (multiple of 9, 9..54)
 * @param customName player-chosen name, or null for the default numbered title
 * @param primary    true if this is the chest that /ec and right-click open
 * @param kind       whether this is a normal or a temporary (overflow) chest
 * @param expiresAt  epoch millis when this chest expires, or null if it never expires
 *                   (drives the static "time remaining" snapshot shown in the dialog)
 */
public record ChestSummary(
        int index,
        int size,
        @Nullable String customName,
        boolean primary,
        ChestKind kind,
        @Nullable Long expiresAt
) {}

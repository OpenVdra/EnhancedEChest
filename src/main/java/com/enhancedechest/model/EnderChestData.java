package com.enhancedechest.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A single ender chest belonging to a player, as loaded from storage for opening.
 *
 * @param owner         the owning player
 * @param index         1-based chest index (also drives the default "Ender Chest N" title)
 * @param size          slot count — always a multiple of 9, 9..54
 * @param customName    player-chosen display name, or null to use the default numbered title
 * @param containerData encoded inventory bytes, or null if the chest has never been saved
 * @param kind          whether this is a normal or a temporary (overflow) chest
 * @param expiresAt     epoch millis when this chest expires, or null if it never expires
 */
public record EnderChestData(
        UUID owner,
        int index,
        int size,
        @Nullable String customName,
        @Nullable byte[] containerData,
        ChestKind kind,
        @Nullable Long expiresAt
) {}

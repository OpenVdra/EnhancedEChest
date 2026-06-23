package com.enhancedechest.model;

/**
 * Whether an ender chest is a permanent ("normal") chest, a transient overflow ("temp") chest, or a
 * permission-granted chest.
 *
 * <p>NORMAL chests are the ones players create and manage; they may carry an optional expiry when
 * granted with a duration. TEMP chests are created automatically when items spill out of a chest
 * that is shrunk, deleted or expired; they always carry an expiry and disappear once emptied.
 * PERM chests are granted/removed automatically from permissions
 * ({@code enhancedechest.additional_amount.<count>.slot.<size>}); to a player they behave exactly
 * like a NORMAL chest (open/rename/icon/set-main), but admin commands never touch them.
 */
public enum ChestKind {

    NORMAL(0),
    TEMP(1),
    PERM(2);

    private final int code;

    ChestKind(int code) {
        this.code = code;
    }

    /** The integer stored in the {@code kind} column. */
    public int code() {
        return code;
    }

    /** Resolves a stored code back to a kind; unknown codes fall back to {@link #NORMAL}. */
    public static ChestKind fromCode(int code) {
        if (code == TEMP.code) return TEMP;
        if (code == PERM.code) return PERM;
        return NORMAL;
    }
}

package com.enhancedechest.model;

/**
 * Whether an ender chest is a permanent ("normal") chest or a transient overflow ("temp") chest.
 *
 * <p>NORMAL chests are the ones players create and manage; they may carry an optional expiry when
 * granted with a duration. TEMP chests are created automatically when items spill out of a chest
 * that is shrunk, deleted or expired; they always carry an expiry and disappear once emptied.
 */
public enum ChestKind {

    NORMAL(0),
    TEMP(1);

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
        return code == TEMP.code ? TEMP : NORMAL;
    }
}

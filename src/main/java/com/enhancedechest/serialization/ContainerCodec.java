package com.enhancedechest.serialization;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * Encodes and decodes a 54-slot inventory for DB storage.
 *
 * Storage format: [1-byte version tag] + [body]. The version tag selects how the body is read,
 * which is what lets the format evolve without orphaning data already in the DB:
 * <ul>
 *   <li><b>0x02 (current)</b> — body is {@link ItemStack#serializeItemsAsBytes(ItemStack[])}.
 *       Stable Paper API, purpose-built for item arrays: it serializes null entries as empty(),
 *       preserves slot positions and array length, and migrates across Minecraft versions on read.</li>
 *   <li><b>0x01 (legacy, read-only)</b> — body is {@link ItemStack#serializeAsBytes()} of a
 *       SHULKER_BOX "vehicle" carrying a CONTAINER data component. Rows written by older builds.
 *       Still decoded here; never written. They re-save as 0x02 the next time a chest is closed
 *       (lazy migration). Do NOT remove this branch — it would orphan un-touched legacy rows.</li>
 * </ul>
 *
 * Slot semantics are positional in both formats: interior empty slots are preserved; trailing
 * empties may be trimmed on encode. Decode pads (or clamps) the tail back to the requested chest
 * size with empty stacks, which is what makes a chest resize safe.
 */
public final class ContainerCodec {

    /** Current write format: version tag + {@link ItemStack#serializeItemsAsBytes(ItemStack[])}. */
    private static final byte FORMAT_VERSION = 0x02;

    /** Legacy format: SHULKER_BOX vehicle carrying a CONTAINER component. Read-only. */
    private static final byte LEGACY_FORMAT_VERSION = 0x01;

    /** Maximum slot count of any ender chest (vanilla double-chest size). */
    public static final int MAX_SIZE = 54;

    /** All chest sizes must be a positive multiple of this. */
    public static final int SLOT_STEP = 9;

    /**
     * Encodes inventory contents to bytes for DB storage in the current format. Null entries in the
     * array are treated as empty (AIR) slots — {@link ItemStack#serializeItemsAsBytes(ItemStack[])}
     * serializes nulls as {@link ItemStack#empty()} itself, preserving slot positions and array
     * length. The array length sets how many slots are encoded; decode() later pads/clamps back to
     * a target size.
     */
    public byte[] encode(ItemStack[] contents) {
        ItemStack[] slots = contents != null ? contents : new ItemStack[0];
        byte[] body = ItemStack.serializeItemsAsBytes(slots);
        byte[] result = new byte[1 + body.length];
        result[0] = FORMAT_VERSION;
        System.arraycopy(body, 0, result, 1, body.length);
        return result;
    }

    /**
     * Decodes stored bytes back to a {@code size}-slot array. Always returns exactly {@code size}
     * entries; empty slots are represented as ItemStack.empty() (never null). Contents stored for
     * a larger size than requested are clamped (trailing slots dropped) — relevant after a resize.
     *
     * @throws CodecException if the data is malformed or uses an unknown format version.
     *                        Callers must NOT open an empty chest on failure — abort and preserve the DB row.
     */
    public ItemStack[] decode(byte[] data, int size) throws CodecException {
        if (data == null || data.length < 2) {
            throw new CodecException("Stored data is too short (length=" + (data == null ? 0 : data.length) + ")");
        }

        byte version = data[0];
        byte[] body = Arrays.copyOfRange(data, 1, data.length);

        return switch (version) {
            case FORMAT_VERSION -> decodeItems(body, size);
            case LEGACY_FORMAT_VERSION -> decodeLegacyVehicle(body, size);
            default -> throw new CodecException("Unknown format version 0x" + String.format("%02X", version)
                    + " — plugin may need updating before this data can be read");
        };
    }

    /** Current format (0x02): body is a serialized ItemStack[]. */
    private ItemStack[] decodeItems(byte[] body, int size) throws CodecException {
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(body);
            return fit(decoded, size);
        } catch (Exception e) {
            throw new CodecException("Failed to deserialize item array", e);
        }
    }

    /** Legacy format (0x01): body is a SHULKER_BOX vehicle carrying a CONTAINER component. */
    @SuppressWarnings("UnstableApiUsage")
    private ItemStack[] decodeLegacyVehicle(byte[] body, int size) throws CodecException {
        try {
            ItemStack vehicle = ItemStack.deserializeBytes(body);
            ItemContainerContents containerContents = vehicle.getData(DataComponentTypes.CONTAINER);
            List<ItemStack> decoded = containerContents != null ? containerContents.contents() : List.of();
            return fit(decoded.toArray(new ItemStack[0]), size);
        } catch (Exception e) {
            throw new CodecException("Failed to deserialize container vehicle", e);
        }
    }

    /** Pads/clamps a decoded array to exactly {@code size} entries, normalizing empties. */
    private static ItemStack[] fit(ItemStack[] decoded, int size) {
        ItemStack[] result = new ItemStack[size];
        Arrays.fill(result, ItemStack.empty());
        int limit = Math.min(decoded.length, size);
        for (int i = 0; i < limit; i++) {
            ItemStack item = decoded[i];
            result[i] = isEmpty(item) ? ItemStack.empty() : item;
        }
        return result;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getType() == Material.AIR;
    }
}

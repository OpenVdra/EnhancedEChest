package com.enhancedechest.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Reads a PlayerVaultsX installation's flat-file vault data and decodes it into Bukkit
 * {@link ItemStack} arrays, ready to be re-encoded into EnhancedEchest's own storage.
 *
 * <p>Unlike AxVaults, PlayerVaultsX keeps <b>no database</b>: every player's vaults live in a single
 * YAML file {@code plugins/PlayerVaults/newvaults/<uuid>.yml} (the plugin's {@code plugin.yml} name is
 * "PlayerVaults", so that is its data folder even though the jar is "PlayerVaultsX"), one key per vault named
 * {@code vault1}, {@code vault2}, … Each value is a MIME-Base64 string of PlayerVaultsX's
 * {@code CardboardBoxSerialization} framing: a big-endian {@code int} slot count, then per slot a
 * big-endian {@code int} byte length followed by that many bytes.
 *
 * <p>On a modern Paper server PlayerVaultsX's {@code CardboardBox} serializes each item with Paper's
 * {@link ItemStack#serializeAsBytes()} (gzip-compressed NBT with a {@code DataVersion}), and encodes
 * an empty/air slot as the single byte {@code 0x0}. So a non-empty slot decodes via
 * {@link ItemStack#deserializeBytes(byte[])} (which runs the DataFixerUpper across versions), and a
 * one-byte {@code 0x0} payload is an empty slot. This matches the data the test/target server itself
 * produces; vault files written by an old non-Paper (Spigot) server use a different CardboardBox
 * framing and are not handled here.
 *
 * <p>This reader is stateless and holds no resources — there is nothing to close. YAML parsing and
 * item deserialization are read-only against the frozen server registries and safe off the main
 * thread (the migration runs on the shared DB executor).
 */
public final class PlayerVaultsXReader {

    /** A single PlayerVaultsX vault as decoded for migration. */
    public record VaultRow(UUID owner, int id, ItemStack[] items) {}

    /** Guards against a corrupt slot count blowing up memory. PlayerVaultsX vaults top out at 54 slots. */
    private static final int MAX_SLOTS = 1024;

    /** The {@code newvaults} folder under {@code plugins/PlayerVaultsX} holding one YAML file per player. */
    private final Path vaultFolder;
    private final Logger log;

    public PlayerVaultsXReader(Path playerVaultsFolder, Logger log) {
        this.vaultFolder = playerVaultsFolder.resolve("newvaults");
        this.log = log;
    }

    /** True if PlayerVaultsX vault data is present (the {@code newvaults} folder holds at least one file). */
    public boolean sourceAvailable() {
        if (!Files.isDirectory(vaultFolder)) {
            return false;
        }
        try (Stream<Path> files = Files.list(vaultFolder)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".yml"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the owner UUIDs of every readable vault file in {@code newvaults}, skipping bad names. */
    public List<UUID> listOwners() throws Exception {
        List<UUID> owners = new ArrayList<>();
        if (!Files.isDirectory(vaultFolder)) {
            return owners;
        }
        try (Stream<Path> files = Files.list(vaultFolder)) {
            files.filter(p -> p.getFileName().toString().endsWith(".yml")).forEach(p -> {
                String name = p.getFileName().toString();
                String stem = name.substring(0, name.length() - ".yml".length());
                try {
                    owners.add(UUID.fromString(stem));
                } catch (Exception e) {
                    log.warn("[PlayerVaultsX] Skipping file with non-UUID name '{}'", name);
                }
            });
        }
        return owners;
    }

    /** Reads and decodes a single player's vaults, ordered by vault number. Empty if they have none. */
    public List<VaultRow> read(UUID owner) throws Exception {
        List<VaultRow> rows = new ArrayList<>();
        File file = vaultFolder.resolve(owner + ".yml").toFile();
        if (!file.isFile()) {
            return rows;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Decode every "vault<n>" key, ordered by n so a player's vault layout carries over directly.
        yaml.getKeys(false).stream()
                .filter(k -> k.startsWith("vault"))
                .map(k -> parseVaultNumber(k))
                .filter(n -> n > 0)
                .sorted()
                .forEach(number -> {
                    String data = yaml.getString("vault" + number);
                    try {
                        ItemStack[] items = decodeVault(data);
                        rows.add(new VaultRow(owner, number, items));
                    } catch (Exception e) {
                        log.warn("[PlayerVaultsX] Failed to decode vault #{} of {} — skipping this vault",
                                number, owner, e);
                    }
                });
        return rows;
    }

    /** Parses the trailing number of a {@code vault<n>} key, or {@code -1} if it is not a vault key. */
    private static int parseVaultNumber(String key) {
        try {
            return Integer.parseInt(key.substring("vault".length()));
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Decodes one PlayerVaultsX vault string into an item array. Empty slots come back as null; the
     * caller fits the array into a chest. Returns an empty array for a null/blank value.
     */
    static ItemStack[] decodeVault(@Nullable String data) throws Exception {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        byte[] blob = Base64.getMimeDecoder().decode(data);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int length = in.readInt();
            if (length < 0 || length > MAX_SLOTS) {
                throw new IllegalArgumentException("Unreasonable slot count " + length + " in PlayerVaultsX blob");
            }
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = in.readInt();
                if (size < 0 || size > MAX_SLOTS * 1024) {
                    throw new IllegalArgumentException("Unreasonable item length " + size + " in PlayerVaultsX blob");
                }
                byte[] itemBytes = new byte[size];
                in.readFully(itemBytes);
                // CardboardBox encodes air/empty as a single 0x0 byte (and never an empty array).
                if (size == 0 || (size == 1 && itemBytes[0] == 0x0)) {
                    items[i] = null;
                    continue;
                }
                items[i] = ItemStack.deserializeBytes(itemBytes);
            }
            return items;
        }
    }
}

package com.enhancedechest.storage.sql;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
import com.enhancedechest.model.PlayerSettings;
import com.enhancedechest.storage.EnderChestStorage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-backed storage shared by all three dialects.
 *
 * All DML below is standard SQL valid for SQLite, MySQL/MariaDB and PostgreSQL; only the
 * schema-creation statement differs per dialect and is injected by subclasses. New chests are
 * inserted at an index computed in Java (max+1), so no dialect-specific upsert is needed.
 */
public abstract class AbstractSqlStorage implements EnderChestStorage {

    private static final String SQL_LIST =
            "SELECT chest_index, size, custom_name, is_primary, kind, expires_at, icon FROM enderchests " +
            "WHERE player_uuid = ? ORDER BY chest_index";

    // is_primary DESC puts the flagged chest first; otherwise the lowest index wins.
    // Only TEMP chests (kind = 1) are excluded — both NORMAL and PERM chests can be opened by /ec and
    // set as the main, so the filter is "anything but TEMP" rather than "NORMAL only".
    private static final String SQL_PRIMARY =
            "SELECT chest_index FROM enderchests WHERE player_uuid = ? AND kind <> 1 " +
            "ORDER BY is_primary DESC, chest_index ASC LIMIT 1";

    private static final String SQL_LOAD =
            "SELECT size, custom_name, container_data, kind, expires_at, icon FROM enderchests " +
            "WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_SAVE =
            "UPDATE enderchests SET container_data = ?, last_updated = ? " +
            "WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_MAX_INDEX =
            "SELECT COALESCE(MAX(chest_index), 0) FROM enderchests WHERE player_uuid = ?";

    private static final String SQL_EXISTS =
            "SELECT 1 FROM enderchests WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_INSERT =
            "INSERT INTO enderchests " +
            "(player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, last_updated, kind, expires_at) " +
            "VALUES (?, ?, ?, NULL, ?, NULL, 0, ?, ?, ?)";

    // Inserts a temp (kind=1) chest carrying spilled item bytes and an expiry.
    private static final String SQL_INSERT_TEMP =
            "INSERT INTO enderchests " +
            "(player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, last_updated, kind, expires_at) " +
            "VALUES (?, ?, ?, NULL, 0, ?, 0, ?, 1, ?)";

    private static final String SQL_RESIZE =
            "UPDATE enderchests SET size = ? WHERE player_uuid = ? AND chest_index = ?";

    // Shrink path: replace both size and contents of the original chest in one statement.
    private static final String SQL_SHRINK_UPDATE =
            "UPDATE enderchests SET size = ?, container_data = ?, last_updated = ? " +
            "WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_FIND_EXPIRED =
            "SELECT player_uuid, chest_index, kind FROM enderchests " +
            "WHERE expires_at IS NOT NULL AND expires_at <= ?";

    // No portable "CREATE INDEX IF NOT EXISTS" across all engines (MySQL 8 lacks it), so this is
    // attempted best-effort in init() and the "already exists" failure is ignored on restart.
    private static final String SQL_CREATE_EXPIRES_INDEX =
            "CREATE INDEX idx_enderchests_expires ON enderchests (expires_at)";

    private static final String SQL_DELETE =
            "DELETE FROM enderchests WHERE player_uuid = ? AND chest_index = ?";

    // Empties a chest in place: NULL contents read back as an empty chest of the same size/name/icon/kind.
    private static final String SQL_CLEAR_CONTENTS =
            "UPDATE enderchests SET container_data = NULL, last_updated = ? " +
            "WHERE player_uuid = ? AND chest_index = ?";

    // Reads every column needed to copy a chest onto another player (used by the transfer transaction).
    private static final String SQL_SELECT_NORMAL_ALL =
            "SELECT chest_index, size, custom_name, is_primary, container_data, icon FROM enderchests " +
            "WHERE player_uuid = ? AND kind = 0 ORDER BY chest_index";

    private static final String SQL_SELECT_NORMAL_ONE =
            "SELECT chest_index, size, custom_name, is_primary, container_data, icon FROM enderchests " +
            "WHERE player_uuid = ? AND kind = 0 AND chest_index = ?";

    // Snapshot of every row of the destination so the transfer can find its max index, scope and collisions.
    private static final String SQL_SELECT_ROWS =
            "SELECT chest_index, size, kind, container_data FROM enderchests WHERE player_uuid = ?";

    // Full insert including custom_name, container_data and icon (NORMAL, never-expiring).
    private static final String SQL_INSERT_FULL =
            "INSERT INTO enderchests " +
            "(player_uuid, chest_index, size, custom_name, is_primary, container_data, migrated, last_updated, kind, expires_at, icon) " +
            "VALUES (?, ?, ?, ?, ?, ?, 0, ?, 0, NULL, ?)";

    // Relocates a row to a free index (moves a destination PERM/TEMP chest off an index the source needs).
    private static final String SQL_REINDEX =
            "UPDATE enderchests SET chest_index = ? WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_RENAME =
            "UPDATE enderchests SET custom_name = ? WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_SET_ICON =
            "UPDATE enderchests SET icon = ? WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_CLEAR_PRIMARY =
            "UPDATE enderchests SET is_primary = 0 WHERE player_uuid = ?";

    private static final String SQL_SET_PRIMARY =
            "UPDATE enderchests SET is_primary = 1 WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_IS_MIGRATED =
            "SELECT migrated FROM enderchests WHERE player_uuid = ? AND chest_index = 1";

    private static final String SQL_SET_MIGRATED =
            "UPDATE enderchests SET migrated = ? WHERE player_uuid = ? AND chest_index = 1";

    // Per-player settings (one row per player). DML is portable; the CREATE is dialect-specific.
    // Save is an UPDATE-else-INSERT upsert (avoids the per-dialect ON CONFLICT / ON DUPLICATE split).
    private static final String SQL_SETTINGS_LOAD =
            "SELECT edit_mode FROM player_settings WHERE player_uuid = ?";

    private static final String SQL_SETTINGS_UPDATE =
            "UPDATE player_settings SET edit_mode = ? WHERE player_uuid = ?";

    private static final String SQL_SETTINGS_INSERT =
            "INSERT INTO player_settings (player_uuid, edit_mode) VALUES (?, ?)";

    protected final HikariDataSource dataSource;

    // Dialect-specific schema-creation statements injected by subclasses to avoid calling abstract
    // methods from the constructor (which would access uninitialized subclass state). Each entry is
    // one CREATE TABLE; they are executed in order on init().
    private final String[] schemaStatements;

    protected AbstractSqlStorage(HikariConfig config, String... schemaStatements) {
        this.dataSource = new HikariDataSource(config);
        this.schemaStatements = schemaStatements;
    }

    @Override
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : schemaStatements) {
                stmt.execute(ddl);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
        // Best-effort secondary index on expires_at for the expiry sweeper. Created here rather than
        // inline because CREATE INDEX has no portable IF NOT EXISTS; an "already exists" error on a
        // subsequent startup is expected and ignored.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(SQL_CREATE_EXPIRES_INDEX);
        } catch (SQLException ignored) {
            // Index already present — not fatal.
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public List<ChestSummary> listChests(UUID owner) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LIST)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<ChestSummary> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new ChestSummary(
                            rs.getInt("chest_index"),
                            rs.getInt("size"),
                            rs.getString("custom_name"),
                            rs.getInt("is_primary") != 0,
                            ChestKind.fromCode(rs.getInt("kind")),
                            getNullableLong(rs, "expires_at"),
                            rs.getString("icon")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list chests for " + owner, e);
        }
    }

    @Override
    public int getPrimaryIndex(UUID owner) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_PRIMARY)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve primary chest for " + owner, e);
        }
    }

    @Override
    public @Nullable EnderChestData loadChest(UUID owner, int index) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOAD)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new EnderChestData(
                        owner,
                        index,
                        rs.getInt("size"),
                        rs.getString("custom_name"),
                        rs.getBytes("container_data"),
                        ChestKind.fromCode(rs.getInt("kind")),
                        getNullableLong(rs, "expires_at"),
                        rs.getString("icon"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void saveChest(UUID owner, int index, byte[] containerData) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SAVE)) {
            ps.setBytes(1, containerData);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, owner.toString());
            ps.setInt(4, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chest " + index + " for " + owner, e);
        }
    }

    @Override
    public int createChest(UUID owner, int size, @Nullable Long expiresAt) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Index is max+1 over ALL rows (temp included) to avoid a PK collision. No chest is
                // ever auto-flagged primary: the main chest is an explicit player choice (setPrimary).
                int newIndex = queryInt(conn, SQL_MAX_INDEX, owner.toString()) + 1;
                insertChest(conn, owner, newIndex, size, false, ChestKind.NORMAL, expiresAt);
                conn.commit();
                return newIndex;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create chest for " + owner, e);
        }
    }

    @Override
    public int createPermChest(UUID owner, int size) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Index is max+1 over ALL rows (temp/perm included) to avoid a PK collision. PERM chests
                // carry no expiry and are never auto-flagged primary (set only via setPrimary).
                int newIndex = queryInt(conn, SQL_MAX_INDEX, owner.toString()) + 1;
                insertChest(conn, owner, newIndex, size, false, ChestKind.PERM, null);
                conn.commit();
                return newIndex;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create perm chest for " + owner, e);
        }
    }

    @Override
    public void ensureChest(UUID owner, int index, int size) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (rowExists(conn, owner, index)) {
                    conn.commit();
                    return;
                }
                // No auto-primary on migration either — the player sets their main explicitly.
                insertChest(conn, owner, index, size, false, ChestKind.NORMAL, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void resizeChest(UUID owner, int index, int size) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RESIZE)) {
            ps.setInt(1, size);
            ps.setString(2, owner.toString());
            ps.setInt(3, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resize chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void deleteChest(UUID owner, int index) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
                    ps.setString(1, owner.toString());
                    ps.setInt(2, index);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void spillShrink(UUID owner, int index, int newSize, byte[] visible,
                            @Nullable byte[] overflow, int tempSize, long tempExpiresAt) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Shrink the original chest in place (new size + trimmed contents).
                try (PreparedStatement ps = conn.prepareStatement(SQL_SHRINK_UPDATE)) {
                    ps.setInt(1, newSize);
                    ps.setBytes(2, visible);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.setString(4, owner.toString());
                    ps.setInt(5, index);
                    ps.executeUpdate();
                }
                // Spill the cut-off items into a fresh temp chest, if any overflowed.
                if (overflow != null) {
                    int tempIndex = queryInt(conn, SQL_MAX_INDEX, owner.toString()) + 1;
                    insertTempChest(conn, owner, tempIndex, tempSize, overflow, tempExpiresAt);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to shrink-spill chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void spillRemove(UUID owner, int index, @Nullable byte[] items, int tempSize, long tempExpiresAt) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert the temp chest BEFORE deleting the original so the items never live in two
                // rows visible to any outside reader — the whole swap is one transaction.
                if (items != null) {
                    int tempIndex = queryInt(conn, SQL_MAX_INDEX, owner.toString()) + 1;
                    insertTempChest(conn, owner, tempIndex, tempSize, items, tempExpiresAt);
                }
                try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
                    ps.setString(1, owner.toString());
                    ps.setInt(2, index);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete-spill chest " + index + " for " + owner, e);
        }
    }

    @Override
    public List<ExpiredRef> findExpired(long now) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_EXPIRED)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                List<ExpiredRef> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new ExpiredRef(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("chest_index"),
                            ChestKind.fromCode(rs.getInt("kind"))));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query expired chests", e);
        }
    }

    @Override
    public void clearChestContents(UUID owner, int index) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CLEAR_CONTENTS)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, owner.toString());
            ps.setInt(3, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear contents of chest " + index + " for " + owner, e);
        }
    }

    @Override
    public int transferChests(UUID from, UUID to, @Nullable Integer onlyIndex,
                              java.util.Set<Integer> preserveDestIndices, long tempExpiresAt) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<SourceRow> src = readSourceNormal(conn, from, onlyIndex);
                if (src.isEmpty()) {
                    conn.commit();
                    return 0;
                }
                List<DestRow> dest = readRows(conn, to);

                int destMax = dest.stream().mapToInt(d -> d.index).max().orElse(0);
                int srcMax  = src.stream().mapToInt(r -> r.index).max().orElse(0);
                // Temp/relocated rows go above everything so they can never collide with an incoming
                // source index (which are all <= srcMax) or an existing destination index.
                int freeIndex = Math.max(destMax, srcMax) + 1;

                java.util.Set<Integer> srcIndices = new java.util.HashSet<>();
                for (SourceRow r : src) srcIndices.add(r.index);

                // Destination NORMAL chests this transfer replaces: the one at onlyIndex, or all of them.
                List<DestRow> destScope = new ArrayList<>();
                for (DestRow d : dest) {
                    if (d.kind != 0) continue;
                    if (onlyIndex == null || d.index == onlyIndex) destScope.add(d);
                }

                // 1. Preserve flagged destination items as temp chests before they are removed.
                for (DestRow d : destScope) {
                    if (preserveDestIndices.contains(d.index) && d.data != null) {
                        insertTempChest(conn, to, freeIndex++, d.size, d.data, tempExpiresAt);
                    }
                }

                // 2. Remove the destination NORMAL chests in scope.
                java.util.Set<Integer> removed = new java.util.HashSet<>();
                for (DestRow d : destScope) {
                    deleteRow(conn, to, d.index);
                    removed.add(d.index);
                }

                // 3. On a full transfer the copied source primary becomes the only main; clear any old flag.
                if (onlyIndex == null) {
                    try (PreparedStatement ps = conn.prepareStatement(SQL_CLEAR_PRIMARY)) {
                        ps.setString(1, to.toString());
                        ps.executeUpdate();
                    }
                }

                // 4. Relocate any surviving destination row (PERM/TEMP, or an out-of-scope NORMAL on a
                //    single-index transfer) that still sits on an index the source is about to occupy.
                for (DestRow d : dest) {
                    if (removed.contains(d.index)) continue;
                    if (srcIndices.contains(d.index)) {
                        reindexRow(conn, to, d.index, freeIndex++);
                    }
                }

                // 5. Write the source chests onto the destination at their original indices.
                for (SourceRow r : src) {
                    boolean primary = onlyIndex == null && r.primary;
                    insertFullChest(conn, to, r.index, r.size, r.name, primary, r.data, r.icon);
                }

                // 6. Remove the source chests (this is a move, not a copy — no duplicate items).
                for (SourceRow r : src) {
                    deleteRow(conn, from, r.index);
                }

                conn.commit();
                return src.size();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to transfer chests from " + from + " to " + to, e);
        }
    }

    private List<SourceRow> readSourceNormal(Connection conn, UUID from, @Nullable Integer onlyIndex)
            throws SQLException {
        String sql = onlyIndex == null ? SQL_SELECT_NORMAL_ALL : SQL_SELECT_NORMAL_ONE;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            if (onlyIndex != null) ps.setInt(2, onlyIndex);
            try (ResultSet rs = ps.executeQuery()) {
                List<SourceRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new SourceRow(
                            rs.getInt("chest_index"),
                            rs.getInt("size"),
                            rs.getString("custom_name"),
                            rs.getInt("is_primary") != 0,
                            rs.getBytes("container_data"),
                            rs.getString("icon")));
                }
                return rows;
            }
        }
    }

    private List<DestRow> readRows(Connection conn, UUID to) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ROWS)) {
            ps.setString(1, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<DestRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new DestRow(
                            rs.getInt("chest_index"),
                            rs.getInt("size"),
                            rs.getInt("kind"),
                            rs.getBytes("container_data")));
                }
                return rows;
            }
        }
    }

    private void deleteRow(Connection conn, UUID owner, int index) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            ps.executeUpdate();
        }
    }

    private void reindexRow(Connection conn, UUID owner, int oldIndex, int newIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_REINDEX)) {
            ps.setInt(1, newIndex);
            ps.setString(2, owner.toString());
            ps.setInt(3, oldIndex);
            ps.executeUpdate();
        }
    }

    private void insertFullChest(Connection conn, UUID owner, int index, int size, @Nullable String name,
                                 boolean primary, @Nullable byte[] data, @Nullable String icon)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_FULL)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            ps.setInt(3, size);
            if (name == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, name);
            ps.setInt(5, primary ? 1 : 0);
            // setBytes(null) binds SQL NULL cleanly across all three dialects (avoids the bytea/blob
            // type guess setNull would need for the container column).
            ps.setBytes(6, data);
            ps.setLong(7, System.currentTimeMillis());
            if (icon == null) ps.setNull(8, Types.VARCHAR); else ps.setString(8, icon);
            ps.executeUpdate();
        }
    }

    /** A source NORMAL chest read in full so it can be copied onto another player. */
    private record SourceRow(int index, int size, @Nullable String name, boolean primary,
                             @Nullable byte[] data, @Nullable String icon) {}

    /** A destination row (any kind), enough to scope, detect collisions and preserve items. */
    private record DestRow(int index, int size, int kind, @Nullable byte[] data) {}

    @Override
    public void renameChest(UUID owner, int index, @Nullable String name) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_RENAME)) {
            if (name == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, name);
            ps.setString(2, owner.toString());
            ps.setInt(3, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rename chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void setIcon(UUID owner, int index, @Nullable String icon) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SET_ICON)) {
            if (icon == null) ps.setNull(1, Types.VARCHAR);
            else ps.setString(1, icon);
            ps.setString(2, owner.toString());
            ps.setInt(3, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set icon for chest " + index + " of " + owner, e);
        }
    }

    @Override
    public void setPrimary(UUID owner, int index) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(SQL_CLEAR_PRIMARY)) {
                    ps.setString(1, owner.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(SQL_SET_PRIMARY)) {
                    ps.setString(1, owner.toString());
                    ps.setInt(2, index);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set primary chest " + index + " for " + owner, e);
        }
    }

    @Override
    public void clearPrimary(UUID owner) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_CLEAR_PRIMARY)) {
            ps.setString(1, owner.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear primary chest for " + owner, e);
        }
    }

    @Override
    public boolean isMigrated(UUID owner) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_IS_MIGRATED)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("migrated") != 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check migrated flag for " + owner, e);
        }
    }

    @Override
    public void setMigrated(UUID owner, boolean migrated) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SET_MIGRATED)) {
            ps.setInt(1, migrated ? 1 : 0);
            ps.setString(2, owner.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update migrated flag for " + owner, e);
        }
    }

    @Override
    public PlayerSettings loadSettings(UUID owner) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SETTINGS_LOAD)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return PlayerSettings.defaults();
                return new PlayerSettings(rs.getInt("edit_mode") != 0);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load settings for " + owner, e);
        }
    }

    @Override
    public void saveSettings(UUID owner, PlayerSettings settings) {
        // Whole-object save. With a single column today this is exactly the edit-mode upsert; when
        // more settings are added, extend this (and the SQL constants) to write every column.
        upsertEditMode(owner, settings.editMode());
    }

    @Override
    public void setEditMode(UUID owner, boolean editMode) {
        upsertEditMode(owner, editMode);
    }

    /**
     * Portable upsert of the edit_mode column: try UPDATE first, INSERT only when no row matched.
     * Avoids the dialect-specific ON CONFLICT / ON DUPLICATE KEY syntax used by native upserts, and
     * needs no preceding read. The UPDATE touches only edit_mode and the INSERT lists only it, so
     * other (future) columns keep their value or fall back to their DB default.
     */
    private void upsertEditMode(UUID owner, boolean editMode) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(SQL_SETTINGS_UPDATE)) {
                    ps.setInt(1, editMode ? 1 : 0);
                    ps.setString(2, owner.toString());
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ps = conn.prepareStatement(SQL_SETTINGS_INSERT)) {
                        ps.setString(1, owner.toString());
                        ps.setInt(2, editMode ? 1 : 0);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save settings for " + owner, e);
        }
    }

    // ---- shared helpers (operate on a caller-managed connection) ----

    private void insertChest(Connection conn, UUID owner, int index, int size, boolean primary,
                             ChestKind kind, @Nullable Long expiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            ps.setInt(3, size);
            ps.setInt(4, primary ? 1 : 0);
            ps.setLong(5, System.currentTimeMillis());
            ps.setInt(6, kind.code());
            setNullableLong(ps, 7, expiresAt);
            ps.executeUpdate();
        }
    }

    /** Inserts a temp (kind=TEMP) chest carrying spilled item bytes and an expiry. */
    private void insertTempChest(Connection conn, UUID owner, int index, int size,
                                 byte[] data, long expiresAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_TEMP)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            ps.setInt(3, size);
            ps.setBytes(4, data);
            ps.setLong(5, System.currentTimeMillis());
            ps.setLong(6, expiresAt);
            ps.executeUpdate();
        }
    }

    /** Reads a nullable BIGINT column as a boxed Long (null when SQL NULL). */
    private static @Nullable Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /** Binds a nullable Long to a BIGINT parameter. */
    private static void setNullableLong(PreparedStatement ps, int idx, @Nullable Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    private boolean rowExists(Connection conn, UUID owner, int index) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SQL_EXISTS)) {
            ps.setString(1, owner.toString());
            ps.setInt(2, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int queryInt(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}

package com.enhancedechest.storage.sql;

import com.enhancedechest.model.ChestKind;
import com.enhancedechest.model.ChestSummary;
import com.enhancedechest.model.EnderChestData;
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
            "SELECT chest_index, size, custom_name, is_primary, kind, expires_at FROM enderchests " +
            "WHERE player_uuid = ? ORDER BY chest_index";

    // is_primary DESC puts the flagged chest first; otherwise the lowest index wins.
    // Temp chests (kind != 0) are never primary and must be excluded from /ec resolution.
    private static final String SQL_PRIMARY =
            "SELECT chest_index FROM enderchests WHERE player_uuid = ? AND kind = 0 " +
            "ORDER BY is_primary DESC, chest_index ASC LIMIT 1";

    private static final String SQL_LOAD =
            "SELECT size, custom_name, container_data, kind, expires_at FROM enderchests " +
            "WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_SAVE =
            "UPDATE enderchests SET container_data = ?, last_updated = ? " +
            "WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_MAX_INDEX =
            "SELECT COALESCE(MAX(chest_index), 0) FROM enderchests WHERE player_uuid = ?";

    private static final String SQL_COUNT =
            "SELECT COUNT(*) FROM enderchests WHERE player_uuid = ?";

    private static final String SQL_COUNT_NORMAL =
            "SELECT COUNT(*) FROM enderchests WHERE player_uuid = ? AND kind = 0";

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

    private static final String SQL_RENAME =
            "UPDATE enderchests SET custom_name = ? WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_CLEAR_PRIMARY =
            "UPDATE enderchests SET is_primary = 0 WHERE player_uuid = ?";

    private static final String SQL_SET_PRIMARY =
            "UPDATE enderchests SET is_primary = 1 WHERE player_uuid = ? AND chest_index = ?";

    private static final String SQL_COUNT_PRIMARY =
            "SELECT COUNT(*) FROM enderchests WHERE player_uuid = ? AND is_primary = 1";

    // Promotion target after a delete: lowest-indexed NORMAL chest (temp chests are never primary).
    private static final String SQL_MIN_INDEX =
            "SELECT chest_index FROM enderchests WHERE player_uuid = ? AND kind = 0 ORDER BY chest_index ASC LIMIT 1";

    private static final String SQL_IS_MIGRATED =
            "SELECT migrated FROM enderchests WHERE player_uuid = ? AND chest_index = 1";

    private static final String SQL_SET_MIGRATED =
            "UPDATE enderchests SET migrated = ? WHERE player_uuid = ? AND chest_index = 1";

    protected final HikariDataSource dataSource;

    // Dialect-specific schema-creation SQL injected by subclasses to avoid calling abstract
    // methods from the constructor (which would access uninitialized subclass state).
    private final String sqlInit;

    protected AbstractSqlStorage(HikariConfig config, String sqlInit) {
        this.dataSource = new HikariDataSource(config);
        this.sqlInit = sqlInit;
    }

    @Override
    public void init() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlInit);
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
                            getNullableLong(rs, "expires_at")));
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
                        getNullableLong(rs, "expires_at"));
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
                // Index is max+1 over ALL rows (temp included) to avoid a PK collision; the primary
                // flag, however, is granted to the first NORMAL chest even if temp chests already exist.
                int newIndex = queryInt(conn, SQL_MAX_INDEX, owner.toString()) + 1;
                boolean first = queryInt(conn, SQL_COUNT_NORMAL, owner.toString()) == 0;
                insertChest(conn, owner, newIndex, size, first, ChestKind.NORMAL, expiresAt);
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
    public void ensureChest(UUID owner, int index, int size) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (rowExists(conn, owner, index)) {
                    conn.commit();
                    return;
                }
                boolean first = queryInt(conn, SQL_COUNT_NORMAL, owner.toString()) == 0;
                insertChest(conn, owner, index, size, first, ChestKind.NORMAL, null);
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
                promotePrimaryIfNeeded(conn, owner);
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
                promotePrimaryIfNeeded(conn, owner);
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

    /** Promotes the lowest-indexed NORMAL chest to primary if no primary flag currently remains. */
    private void promotePrimaryIfNeeded(Connection conn, UUID owner) throws SQLException {
        if (queryInt(conn, SQL_COUNT_PRIMARY, owner.toString()) != 0) {
            return;
        }
        int min = queryInt(conn, SQL_MIN_INDEX, owner.toString());
        if (min > 0) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_SET_PRIMARY)) {
                ps.setString(1, owner.toString());
                ps.setInt(2, min);
                ps.executeUpdate();
            }
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

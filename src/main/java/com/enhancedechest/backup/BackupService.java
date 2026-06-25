package com.enhancedechest.backup;

import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Takes scheduled, self-pruning snapshots of the database.
 *
 * <p>Runs on a FoliaLib async repeating timer at the configured {@code interval} and writes a fresh
 * file via {@link EnderChestStorage#backup(Path)} (SQLite {@code VACUUM INTO} — consistent even while
 * players are saving, so the hot path is never paused). Only the file-based SQLite backend supports
 * this; for remote backends {@link EnderChestStorage#supportsBackup()} is false and the service logs
 * a one-time warning and stays idle (those should be backed up with the DB server's own tooling).
 *
 * <p>After each snapshot the {@code backups/} folder is pruned to the most recent {@code keep} files
 * ({@code keep <= 0} keeps everything). Snapshot names embed a sortable timestamp so lexical order is
 * chronological order. Everything runs off the region/main thread; failures are logged, never thrown,
 * so a bad backup can never take the server down or corrupt the live DB.
 */
public final class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "enderchests-";
    private static final String SUFFIX = ".db";

    private final EnderChestStorage storage;
    private final FoliaLib foliaLib;
    private final Logger logger;
    private final Path backupDir;
    private final String backendName;

    // Runtime-tunable via /ee reload (see reschedule). Only touched on the main thread (start/stop/
    // reschedule); the async backup reads only backupDir and keep, the latter being a cheap int read.
    private boolean enabled;
    private long intervalMillis;
    private volatile int keep;

    private WrappedTask task;

    public BackupService(EnderChestStorage storage, FoliaLib foliaLib, Logger logger, Path dataFolder,
                         boolean enabled, long intervalMillis, int keep, String folderName,
                         String backendName) {
        this.storage        = storage;
        this.foliaLib       = foliaLib;
        this.logger         = logger;
        this.backupDir      = dataFolder.resolve(folderName);
        this.enabled        = enabled;
        this.intervalMillis = intervalMillis;
        this.keep           = keep;
        this.backendName    = backendName;
    }

    /** Starts the repeating async backup, if enabled and the backend supports file snapshots. */
    public void start() {
        if (!enabled) {
            return;
        }
        if (!storage.supportsBackup()) {
            logger.warn("Auto-backup is enabled but the '{}' storage backend cannot be snapshotted by "
                    + "this plugin — use your database server's own backup tooling instead. Skipping.",
                    backendName);
            return;
        }
        task = foliaLib.getScheduler().runTimerAsync(
                this::backupNow, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /** Runs one snapshot off-thread immediately (used for the optional on-startup backup). */
    public void backupNowAsync() {
        if (!enabled || !storage.supportsBackup()) {
            return;
        }
        foliaLib.getScheduler().runAsync(t -> backupNow());
    }

    /**
     * Re-applies possibly-changed backup settings after a {@code /ee reload}. A changed retention
     * count takes effect on the next prune without touching the timer; a changed enabled flag or
     * interval restarts the timer (cancelling the old one — never more than one live task, so no leak).
     * The backup folder is bound at startup and a change to it requires a restart.
     */
    public void reschedule(boolean newEnabled, long newIntervalMillis, int newKeep) {
        this.keep = newKeep;
        boolean changed = newEnabled != enabled || newIntervalMillis != intervalMillis;
        this.enabled        = newEnabled;
        this.intervalMillis = newIntervalMillis;
        if (!changed) {
            return;
        }
        stop();
        start();
    }

    /** Cancels the repeating backup. Safe to call even if never started. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** One snapshot + prune. Runs on a FoliaLib async thread; logs and swallows all failures. */
    private void backupNow() {
        try {
            Files.createDirectories(backupDir);
            Path target = uniqueTarget();
            storage.backup(target);
            logger.info("Database backup written: {}", target.getFileName());
            prune();
        } catch (Exception e) {
            logger.error("Database backup failed", e);
        }
    }

    /**
     * A timestamped target that does not yet exist (VACUUM INTO refuses an existing file). The plain
     * second-resolution name is used normally; a nano-suffixed name is the fallback in the rare case
     * two backups land within the same second.
     */
    private Path uniqueTarget() {
        String stamp = LocalDateTime.now().format(STAMP);
        Path target = backupDir.resolve(PREFIX + stamp + SUFFIX);
        if (Files.exists(target)) {
            target = backupDir.resolve(PREFIX + stamp + "-" + System.nanoTime() + SUFFIX);
        }
        return target;
    }

    /** Deletes the oldest snapshots beyond the retention count ({@code keep <= 0} keeps everything). */
    private void prune() {
        int retain = keep;
        if (retain <= 0) {
            return;
        }
        try (Stream<Path> files = Files.list(backupDir)) {
            List<Path> backups = files
                    .filter(BackupService::isBackupFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            int excess = backups.size() - retain;
            for (int i = 0; i < excess; i++) {
                Path old = backups.get(i);
                try {
                    Files.deleteIfExists(old);
                } catch (IOException e) {
                    logger.warn("Could not delete old backup {}: {}", old.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("Could not prune old backups: {}", e.getMessage());
        }
    }

    private static boolean isBackupFile(Path p) {
        String name = p.getFileName().toString();
        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
    }
}

package com.enhancedechest.expiry;

import com.enhancedechest.gui.EnderChestService;
import com.enhancedechest.model.ChestKind;
import com.enhancedechest.storage.EnderChestStorage;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans for expired chests and disposes of them.
 *
 * <p>Runs on a FoliaLib async repeating timer at the configured {@code check-interval}. Each sweep
 * queries the (indexed) {@code expires_at} column — cheap, since it is NULL for almost every row —
 * and routes each hit through {@link EnderChestService}:
 * <ul>
 *   <li>a NORMAL chest is removed with its items spilled into a temp chest ({@code force = false});</li>
 *   <li>a TEMP chest is hard-deleted, its remaining items lost ({@code force = true}).</li>
 * </ul>
 * Both paths reuse the service's force-close + per-(owner,index) serialization, so expiry is just as
 * dupe-safe as a manual delete. Centralising the dangerous mutation here keeps the hot open/close
 * path free of any expiry filtering.
 */
public final class ExpirySweeper {

    private final EnderChestService service;
    private final EnderChestStorage storage;
    private final FoliaLib foliaLib;
    private final Logger logger;
    // Runtime-tunable via /ee reload (see reschedule). Only touched on the main thread (start/stop/
    // reschedule); the async sweep never reads it, so no extra synchronisation is needed.
    private long intervalMillis;

    private WrappedTask task;

    public ExpirySweeper(EnderChestService service, EnderChestStorage storage,
                         FoliaLib foliaLib, Logger logger, long intervalMillis) {
        this.service        = service;
        this.storage        = storage;
        this.foliaLib       = foliaLib;
        this.logger         = logger;
        this.intervalMillis = intervalMillis;
    }

    /** Starts the repeating async sweep. Safe to call once during plugin enable. */
    public void start() {
        task = foliaLib.getScheduler().runTimerAsync(
                this::sweep, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Re-applies a possibly-changed sweep interval after a {@code /ee reload}.
     *
     * <p>No-op when the interval is unchanged, so repeated reloads neither reset the countdown nor
     * churn timers. When it does change and the sweeper is running, the old timer is cancelled and a
     * fresh one started — there is never more than one live task, so no leak. If the sweeper is not
     * running the value is simply stored for the next {@link #start()}.
     */
    public void reschedule(long newIntervalMillis) {
        if (newIntervalMillis == intervalMillis) {
            return;
        }
        intervalMillis = newIntervalMillis;
        if (task != null) {
            stop();
            start();
        }
    }

    /** Cancels the repeating sweep. Safe to call even if never started. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** One sweep: find expired chests and dispose of each. Runs on a FoliaLib async thread. */
    private void sweep() {
        try {
            List<EnderChestStorage.ExpiredRef> expired = storage.findExpired(System.currentTimeMillis());
            for (EnderChestStorage.ExpiredRef ref : expired) {
                // TEMP → hard delete (items lost); NORMAL → spill items into a fresh temp chest.
                service.removeChest(ref.owner(), ref.index(), ref.kind() == ChestKind.TEMP);
            }
        } catch (Exception e) {
            logger.error("Expiry sweep failed", e);
        }
    }
}

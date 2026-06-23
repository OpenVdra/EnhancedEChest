package com.enhancedechest.listener;

import com.enhancedechest.service.PlayerSettingsCache;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drives the lifecycle of the per-player settings write-through cache: preload on join, evict on quit.
 *
 * <p>These two handlers are the cache's only lifecycle hooks, and they pair up exactly — every entry
 * added by a join preload is removed by the matching quit eviction — so the cache stays bounded by the
 * online-player count and never leaks. (The join-then-immediate-quit race is handled inside
 * {@link PlayerSettingsCache#preloadSettings}.) Separate from {@link JoinMigrationListener}, which
 * early-returns when migration is off and so must not be relied on to run the preload.
 */
@RequiredArgsConstructor
public final class PlayerSettingsListener implements Listener {

    private final PlayerSettingsCache settings;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        settings.preloadSettings(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        settings.evictSettings(event.getPlayer().getUniqueId());
    }
}

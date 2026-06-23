package com.enhancedechest.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared cached daemon thread-pool for all asynchronous storage work ({@code EnhancedEchest-db}).
 *
 * <p>Owns the single executor every collaborator dispatches DB reads/writes onto, replacing the
 * scattered {@code CompletableFuture.supplyAsync(..., asyncExecutor)} pattern with {@link #supply}
 * / {@link #run}. The pool is closed once, last, on plugin disable (after sessions have flushed their
 * pending saves) — see {@link #shutdown()}.
 */
public final class DbExecutor {

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "EnhancedEchest-db");
        t.setDaemon(true);
        return t;
    });

    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor);
    }

    public CompletableFuture<Void> run(Runnable work) {
        return CompletableFuture.runAsync(work, executor);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

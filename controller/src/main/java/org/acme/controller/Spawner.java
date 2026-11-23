package org.acme.controller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Launches the fleet's first moles, then adds new ones, as fast as they can start: each mole is launched as soon as the
 * one before it has painted its window. The spawn rate is how fast a mole starts, so native moles come much faster than JVM ones. When the board
 * is full, the spawner stops and runs its game-over action.
 */
public class Spawner {

    /** How long to wait for a mole to say it's ready before launching the next one anyway. */
    private static final long READY_TIMEOUT_SECONDS = 5;

    private final Fleet fleet;
    private final Runnable onFull;
    private final Thread thread;
    private volatile boolean stopped;

    /** @param onFull run once, when there's no empty slot left for a new mole */
    public Spawner(Fleet fleet, Runnable onFull) {
        this.fleet = fleet;
        this.onFull = onFull;
        this.thread = Thread.ofPlatform().daemon().name("mole-spawner").unstarted(this::run);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        stopped = true;
        thread.interrupt();
    }

    private void run() {
        for (Tile tile : fleet.firstSlots()) {
            if (stopped || !awaitReady(fleet.startOne(tile))) {
                return;
            }
        }
        while (!stopped) {
            CompletableFuture<Void> ready = fleet.spawn();
            if (ready == null) {
                onFull.run();
                return;
            }
            if (!awaitReady(ready)) {
                return;
            }
        }
    }

    /** Waits for a mole to be ready, or for a while; returns false if the spawner should stop. */
    private boolean awaitReady(CompletableFuture<Void> ready) {
        try {
            ready.get(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            return false;
        } catch (TimeoutException e) {
            System.err.println("A new mole didn't say it was ready within " + READY_TIMEOUT_SECONDS
                    + " s; launching the next anyway. If this keeps happening, the mole build may be out of date: "
                    + "rebuild it with ./mvnw package -pl mole (add -Dnative for native moles)");
            return true;
        } catch (ExecutionException e) {
            System.err.println("Stopped adding moles: " + e.getCause().getMessage());
            return false;
        }
    }
}

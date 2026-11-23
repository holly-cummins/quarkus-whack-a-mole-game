package org.acme.controller;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The running moles, playing a {@link Game}.
 * <p>
 * In whack-a-mole, one mole is the target, the only one the player can hit. Whenever a mole closes, a replacement
 * opens in a random empty slot; when the target closes, its replacement is the new target, so the target jumps to a
 * random empty slot, and is always a freshly started mole.
 * <p>
 * In mole apocalypse, every mole is a target and closed moles leave gaps. A {@link Spawner} calls {@link #spawn()} to
 * add new moles, until the board is full.
 * <p>
 * Thread-safe: moles close on JDK process-reaper threads, the spawner has its own thread, and {@link #stop()} runs on a
 * shutdown hook. Launching and stopping share the fleet's lock, so no mole can be launched after the fleet has stopped
 * and be left running.
 */
public class Fleet {

    /** A mole that fails this quickly is broken rather than closed, so relaunching it would just loop. */
    private static final Duration CRASH_WINDOW = Duration.ofSeconds(1);

    private final Board board;
    private final MoleLauncher launcher;
    private final Game game;
    private final Set<Process> processes = new HashSet<>();
    private int moles;
    private int whacked;
    private boolean stopped;

    public Fleet(Board board, MoleLauncher launcher, Game game) {
        this.board = board;
        this.launcher = launcher;
        this.game = game;
    }

    /** Launches a mole in every slot the board starts with, all at once, and returns how many are running. */
    public synchronized int start() {
        List<Tile> initial = board.initial();
        Tile target = initial.get(ThreadLocalRandom.current().nextInt(initial.size()));
        initial.forEach(tile -> launch(tile, game.everyMoleIsATarget() || tile.equals(target), false));
        return moles;
    }

    /**
     * Launches one of the moles the board starts with. Returns a future that completes once the mole has painted its
     * window.
     */
    public synchronized CompletableFuture<Void> startOne(Tile tile) {
        return launch(tile, game.everyMoleIsATarget(), false);
    }

    /** The slots the board starts with a mole in, in the order they should appear. */
    public List<Tile> firstSlots() {
        return board.initial();
    }

    /**
     * Adds a mole in an empty slot. Returns a future that completes once the mole has painted its window, or null if
     * the board is full.
     */
    public synchronized CompletableFuture<Void> spawn() {
        if (stopped) {
            return CompletableFuture.failedFuture(new IllegalStateException("the game has stopped"));
        }
        Tile tile = board.claim();
        return tile == null ? null : launch(tile, game.everyMoleIsATarget(), true);
    }

    /** How many moles the player has closed. */
    public synchronized int whacked() {
        return whacked;
    }

    /**
     * Shows the game-over screen. The player can't close it, so it stays up until the fleet is stopped; if it goes
     * anyway, the fleet stops too.
     */
    public synchronized void gameOver(Tile screen, String score) {
        if (stopped) {
            return;
        }
        try {
            Process banner = launcher.launchGameOver(screen, score);
            processes.add(banner);
            banner.onExit().thenRun(this::stop);
        } catch (Exception e) {
            System.err.println("Could not show the game-over screen: " + e.getMessage());
            stop();
        }
    }

    /** Closes every mole, and stops replacing them. */
    public synchronized void stop() {
        stopped = true;
        processes.forEach(Process::destroy);
        notifyAll();
    }

    /** Waits until the fleet is stopped, or until no moles are left because none could be relaunched. */
    public synchronized void await() throws InterruptedException {
        while (!stopped && !(moles == 0 && game.replacesClosedMoles())) {
            wait();
        }
    }

    /** Returns a future that completes once the mole has painted its window. */
    private synchronized CompletableFuture<Void> launch(Tile tile, boolean target, boolean replacement) {
        if (stopped) {
            return CompletableFuture.failedFuture(new IllegalStateException("the game has stopped"));
        }
        long launchedAt = System.nanoTime();
        MoleLauncher.Launched launched;
        try {
            launched = launcher.launch(tile, target, replacement);
        } catch (Exception e) {
            System.err.println("Could not launch mole " + tile.id() + ": " + e.getMessage());
            board.vacate(tile);
            return CompletableFuture.failedFuture(e);
        }
        Process mole = launched.process();
        processes.add(mole);
        moles++;
        mole.onExit().thenAccept(exited -> closed(tile, target, exited, Duration.ofNanos(System.nanoTime() - launchedAt)));
        return launched.ready();
    }

    private void closed(Tile tile, boolean target, Process mole, Duration lifetime) {
        synchronized (this) {
            processes.remove(mole);
            moles--;
            if (stopped) {
                return;
            }
            if (mole.exitValue() != 0 && lifetime.compareTo(CRASH_WINDOW) < 0) {
                System.err.printf("Mole %d exited with code %d after %d ms, so its slot stays empty%n", tile.id(),
                        mole.exitValue(), lifetime.toMillis());
                if (target && !game.everyMoleIsATarget()) {
                    System.err.println("That was the target, so there is no target now");
                }
                board.vacate(tile);
                notifyAll();
                return;
            }
            whacked++;
            if (!game.replacesClosedMoles()) {
                board.vacate(tile);
                return;
            }
        }
        launch(board.move(tile), target, true);
    }
}

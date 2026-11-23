package org.acme.controller;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The running moles. Whenever one closes, a replacement opens in a random empty slot of the board, until the fleet is
 * stopped.
 * <p>
 * One mole is the target, the only one the player can hit. When the target closes, its replacement is the new target,
 * so the target jumps to a random empty slot, and is always a freshly started mole.
 * <p>
 * Thread-safe: moles close on JDK process-reaper threads, while {@link #stop()} runs on a shutdown hook. Launching and
 * stopping share the fleet's lock, so no mole can be launched after the fleet has stopped and be left running.
 */
public class Fleet {

    /** A mole that fails this quickly is broken rather than closed, so relaunching it would just loop. */
    private static final Duration CRASH_WINDOW = Duration.ofSeconds(1);

    private final Board board;
    private final MoleLauncher launcher;
    private final Set<Process> moles = new HashSet<>();
    private boolean stopped;

    public Fleet(Board board, MoleLauncher launcher) {
        this.board = board;
        this.launcher = launcher;
    }

    /** Launches a mole in every slot the board starts with, one of them the target, and returns how many are running. */
    public synchronized int start() {
        List<Tile> initial = board.initial();
        Tile target = initial.get(ThreadLocalRandom.current().nextInt(initial.size()));
        initial.forEach(tile -> launch(tile, tile.equals(target), false));
        return moles.size();
    }

    /** Closes every mole, and stops replacing them. */
    public synchronized void stop() {
        stopped = true;
        moles.forEach(Process::destroy);
        notifyAll();
    }

    /** Waits until the fleet is stopped, or until no moles are left because none could be relaunched. */
    public synchronized void await() throws InterruptedException {
        while (!stopped && !moles.isEmpty()) {
            wait();
        }
    }

    private synchronized void launch(Tile tile, boolean target, boolean replacement) {
        if (stopped) {
            return;
        }
        long launchedAt = System.nanoTime();
        Process mole;
        try {
            mole = launcher.launch(tile, target, replacement);
        } catch (Exception e) {
            System.err.println("Could not launch mole " + tile.id() + ": " + e.getMessage());
            board.vacate(tile);
            return;
        }
        moles.add(mole);
        mole.onExit().thenAccept(exited -> closed(tile, target, exited, Duration.ofNanos(System.nanoTime() - launchedAt)));
    }

    private void closed(Tile tile, boolean target, Process mole, Duration lifetime) {
        synchronized (this) {
            moles.remove(mole);
            if (stopped) {
                return;
            }
            if (mole.exitValue() != 0 && lifetime.compareTo(CRASH_WINDOW) < 0) {
                System.err.printf("Mole %d exited with code %d after %d ms, so its slot stays empty%n", tile.id(),
                        mole.exitValue(), lifetime.toMillis());
                if (target) {
                    System.err.println("That was the target, so there is no target now");
                }
                board.vacate(tile);
                notifyAll();
                return;
            }
        }
        launch(board.move(tile), target, true);
    }
}

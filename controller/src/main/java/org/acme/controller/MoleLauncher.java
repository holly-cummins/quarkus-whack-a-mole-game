package org.acme.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.smallrye.common.process.ProcessBuilder;
import io.smallrye.common.process.ProcessUtil;

/**
 * Builds the command line for a mole, as a native executable or on the JVM, and starts it.
 * <p>
 * Processes are started with SmallRye Common Process, which Quarkus itself uses in place of the JDK's
 * {@link java.lang.ProcessBuilder}.
 */
public class MoleLauncher {

    public enum Mode { AUTO, NATIVE, JVM }

    static final Path NATIVE_EXECUTABLE = Path.of("mole/target/mole-1.0.0-SNAPSHOT-runner");
    static final Path JAR = Path.of("mole/target/quarkus-app/quarkus-run.jar");

    private final Mode mode;
    private final boolean borderless;
    private final Set<ProcessHandle> running = ConcurrentHashMap.newKeySet();

    public MoleLauncher(Mode mode, boolean borderless) {
        this.mode = resolve(mode);
        this.borderless = borderless;
    }

    public Mode mode() {
        return mode;
    }

    /**
     * @param spawnedAtMicros when the mole is being launched, in microseconds since the epoch, so it can show how
     *                        long it took to appear
     */
    public List<String> command(Tile tile, long spawnedAtMicros) {
        List<String> command = new ArrayList<>();
        if (mode == Mode.NATIVE) {
            command.add(NATIVE_EXECUTABLE.toString());
        } else {
            command.add(ProcessUtil.pathOfJava().toString());
            if (isMac()) {
                // SWT must own the main thread on macOS
                command.add("-XstartOnFirstThread");
            }
        }
        command.add("-Dmole.id=" + tile.id());
        command.add("-Dmole.x=" + tile.x());
        command.add("-Dmole.y=" + tile.y());
        command.add("-Dmole.width=" + tile.width());
        command.add("-Dmole.height=" + tile.height());
        command.add("-Dmole.borderless=" + borderless);
        command.add("-Dmole.spawned-at=" + spawnedAtMicros);
        if (mode == Mode.JVM) {
            command.add("-jar");
            command.add(JAR.toString());
        }
        return command;
    }

    /**
     * Starts a mole. The process is started before this returns.
     *
     * @return a future that completes when the mole exits, for whatever reason
     */
    public CompletableFuture<Void> launch(Tile tile) {
        // Take the timestamp as late as possible, so the mole's startup time covers only the launch itself
        List<String> command = command(tile, nowMicros());
        return ProcessBuilder.newBuilder(Path.of(command.getFirst()), command.subList(1, command.size()))
                // A mole that's closed by the player or stopped by the controller has done its job, whatever its exit code
                .exitCodeChecker(exitCode -> true)
                // These timeouts would terminate a mole a few seconds after its I/O finished. Moles live until closed.
                .softExitTimeout(null)
                .hardExitTimeout(null)
                // Waiting here keeps the process counted as busy until it exits, and gives us a handle to stop it
                .whileRunning(handle -> {
                    running.add(handle);
                    try {
                        handle.waitUninterruptiblyFor();
                    } finally {
                        running.remove(handle);
                    }
                })
                .output().inherited()
                // Pass stderr straight through, rather than collecting it to log a warning when a mole that wrote
                // to stderr exits cleanly
                .error().logOnSuccess(false).gatherOnFail(false).inherited()
                .runAsync();
    }

    /**
     * SmallRye Common Process removes a shutdown hook when each process finishes. If that happens while the JVM is
     * already shutting down, which it does when the controller stops its moles, the JDK throws and the library doesn't
     * catch it. Silence that one case, which is harmless, and report everything else as usual.
     */
    static void ignoreProcessCleanupDuringShutdown() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            if (e instanceof IllegalStateException && "Shutdown in progress".equals(e.getMessage())
                    && thread.getName().startsWith("process-")) {
                return;
            }
            if (previous != null) {
                previous.uncaughtException(thread, e);
            } else {
                thread.getThreadGroup().uncaughtException(thread, e);
            }
        });
    }

    /** Asks every running mole to exit. */
    public void stopAll() {
        running.forEach(ProcessHandle::destroy);
    }

    static long nowMicros() {
        return ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now());
    }

    private static Mode resolve(Mode requested) {
        if (requested != Mode.AUTO) {
            return requested;
        }
        return Files.isExecutable(NATIVE_EXECUTABLE) ? Mode.NATIVE : Mode.JVM;
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
}

package org.acme.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Builds the command line for a mole, as a native executable, on the JVM, or on the JVM with a Leyden AOT cache, and
 * starts it.
 */
public class MoleLauncher {

    public enum Mode {
        AUTO,
        NATIVE,
        JVM,
        /**
         * On the JVM, with the AOT cache from a Leyden build of the mole ({@code ./mvnw verify -pl mole -Dleyden}). The
         * cache only works with the exact JDK that built it, so run the controller on that JDK.
         */
        LEYDEN
    }

    static final Path NATIVE_EXECUTABLE = Path.of("mole/target/mole-1.0.0-SNAPSHOT-runner");
    static final Path JAR = Path.of("mole/target/quarkus-app/quarkus-run.jar");
    static final Path AOT_CACHE = Path.of("mole/target/quarkus-app/app.aot");

    /** What a mole prints on its own line once it has painted its window. */
    static final String READY = "mole-ready";

    /**
     * A mole that has been started.
     *
     * @param ready completes once the mole has painted its window, or has exited
     */
    public record Launched(Process process, CompletableFuture<Void> ready) {
    }

    /** Quarkus's own profiles, which describe how the controller is running rather than which game it plays. */
    private static final Set<String> BUILT_IN_PROFILES = Set.of("prod", "dev", "test");

    private final Mode mode;
    private final boolean borderless;
    private final List<String> profiles;

    /**
     * @param profiles the controller's active config profiles. Any besides Quarkus's own are passed to each mole, so
     *                 the moles can have settings for the game too, such as {@code %apocalypse.mole...}.
     */
    public MoleLauncher(Mode mode, boolean borderless, List<String> profiles) {
        this.mode = resolve(mode);
        this.borderless = borderless;
        this.profiles = profiles.stream().filter(profile -> !BUILT_IN_PROFILES.contains(profile)).toList();
    }

    public Mode mode() {
        return mode;
    }

    /**
     * @param target          whether this mole is one the player has to hit
     * @param replacement     whether this mole appears during the game, rather than at the start, so it should make
     *                        an entrance
     * @param spawnedAtMicros when the mole is being launched, in microseconds since the epoch, so it can show how
     *                        long it took to appear
     */
    public List<String> command(Tile tile, boolean target, boolean replacement, long spawnedAtMicros) {
        Map<String, Object> properties = geometry(tile);
        properties.put("mole.borderless", borderless);
        properties.put("mole.target", target);
        properties.put("mole.replacement", replacement);
        properties.put("mole.spawned-at", spawnedAtMicros);
        return command(properties);
    }

    /** The game-over screen, covering the given area, with the player's score. */
    public List<String> gameOverCommand(Tile screen, String score) {
        Map<String, Object> properties = geometry(screen);
        properties.put("mole.role", "GAME_OVER");
        properties.put("mole.message", score);
        return command(properties);
    }

    public Launched launch(Tile tile, boolean target, boolean replacement) throws IOException {
        // Take the timestamp as late as possible, so the mole's startup time covers only the launch itself
        Process mole = new ProcessBuilder(command(tile, target, replacement, nowMicros()))
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        return new Launched(mole, watchForReady(mole));
    }

    public Process launchGameOver(Tile screen, String score) throws IOException {
        return new ProcessBuilder(gameOverCommand(screen, score)).inheritIO().start();
    }

    /**
     * Reads the mole's output on a virtual thread, completing the future when the mole says it's ready, and passing
     * everything else through to the controller's own output.
     */
    private static CompletableFuture<Void> watchForReady(Process mole) {
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Thread.ofVirtual().name("mole-output-" + mole.pid()).start(() -> {
            try (BufferedReader output = mole.inputReader()) {
                for (String line = output.readLine(); line != null; line = output.readLine()) {
                    if (line.equals(READY)) {
                        ready.complete(null);
                    } else {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                // The mole has gone; it can't get any readier
            } finally {
                ready.complete(null);
            }
        });
        return ready;
    }

    private static Map<String, Object> geometry(Tile tile) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mole.id", tile.id());
        properties.put("mole.x", tile.x());
        properties.put("mole.y", tile.y());
        properties.put("mole.width", tile.width());
        properties.put("mole.height", tile.height());
        return properties;
    }

    private List<String> command(Map<String, Object> properties) {
        List<String> command = new ArrayList<>();
        if (mode == Mode.NATIVE) {
            command.add(NATIVE_EXECUTABLE.toString());
        } else {
            command.add(javaExecutable());
            if (isMac()) {
                // SWT must own the main thread on macOS
                command.add("-XstartOnFirstThread");
            }
            if (mode == Mode.LEYDEN) {
                command.add("-XX:AOTCache=" + AOT_CACHE);
            }
        }
        properties.forEach((name, value) -> command.add("-D" + name + "=" + value));
        if (!profiles.isEmpty()) {
            command.add("-Dquarkus.profile=" + String.join(",", profiles));
        }
        if (mode == Mode.JVM || mode == Mode.LEYDEN) {
            command.add("-jar");
            command.add(JAR.toString());
        }
        return command;
    }

    /** Checks the mole has been built for this mode, and explains how to build it if not. */
    public void checkBuilt() {
        Path needed = switch (mode) {
            case NATIVE -> NATIVE_EXECUTABLE;
            case LEYDEN -> AOT_CACHE;
            default -> JAR;
        };
        if (!Files.exists(needed)) {
            throw new IllegalStateException("No " + needed + " for " + mode + " moles; build it with ./mvnw "
                    + switch (mode) {
                        case NATIVE -> "package -pl mole -Dnative";
                        case LEYDEN -> "verify -pl mole -Dleyden (the training run needs a display)";
                        default -> "package -pl mole";
                    });
        }
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

    /** Use the controller's own JVM if it has one; a native controller has no java.home, so fall back to the PATH. */
    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        return javaHome == null ? "java" : Path.of(javaHome, "bin", "java").toString();
    }

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
}

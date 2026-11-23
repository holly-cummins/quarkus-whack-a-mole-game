package org.acme.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds the command line for a mole, as a native executable or on the JVM, and starts it. */
public class MoleLauncher {

    public enum Mode { AUTO, NATIVE, JVM }

    static final Path NATIVE_EXECUTABLE = Path.of("mole/target/mole-1.0.0-SNAPSHOT-runner");
    static final Path JAR = Path.of("mole/target/quarkus-app/quarkus-run.jar");

    private final Mode mode;
    private final boolean borderless;

    public MoleLauncher(Mode mode, boolean borderless) {
        this.mode = resolve(mode);
        this.borderless = borderless;
    }

    public Mode mode() {
        return mode;
    }

    public List<String> command(Tile tile) {
        List<String> command = new ArrayList<>();
        if (mode == Mode.NATIVE) {
            command.add(NATIVE_EXECUTABLE.toString());
        } else {
            command.add(javaExecutable());
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
        if (mode == Mode.JVM) {
            command.add("-jar");
            command.add(JAR.toString());
        }
        return command;
    }

    public Process launch(Tile tile) throws IOException {
        return new ProcessBuilder(command(tile)).inheritIO().start();
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

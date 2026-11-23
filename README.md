# Quarkus whack-a-mole

A demo of Quarkus start-up speed: a controller launches a fleet of mole windows that tile the screen.

The repo has two Quarkus applications:

| Module | What it is |
|---|---|
| `mole` | One SWT window. Its position and size come from system properties (`-Dmole.x`, `-Dmole.y`, `-Dmole.width`, `-Dmole.height`, `-Dmole.borderless`), so it paints without waiting on the network. |
| `controller` | A picocli CLI (`moles`) that works out the tile layout and spawns one mole process per tile. |

## Build

```
./mvnw package                 # JVM build of both
./mvnw package -Dnative -pl mole   # native mole (the controller can stay on the JVM)
```

## Run

Run from the repository root, because the controller finds the mole under `mole/target/`:

```
java -jar controller/target/quarkus-app/quarkus-run.jar launch --columns 4 --rows 3 --screen-width 1920 --screen-height 1080
```

If a native mole has been built, it's used automatically. Use `--mode JVM` or `--mode NATIVE` to choose. Add `--dry-run` to print the commands instead of running them, and `--help` to see all options.

Stopping the controller (Ctrl-C) closes every mole it started.

Notes:
- SWT is not compatible with dev mode on Mac.
- On macOS, JVM moles are started with `-XstartOnFirstThread`; the controller adds this for you.
- On Linux under Wayland, apps can't position their own windows. Run with `GDK_BACKEND=x11`.

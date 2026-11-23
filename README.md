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
./mvnw package -Dnative        # native build of both
./mvnw package -Dnative -pl mole   # native mole only (the controller can stay on the JVM)
```

Native builds need [Mandrel](https://github.com/graalvm/mandrel/releases) or GraalVM for JDK 25.0.4 or newer. The easiest way is [mise](https://mise.jdx.dev): `mise.toml` pins Mandrel 25.0.5, so in this directory `mise install` fetches it and sets `JAVA_HOME`, which is all Quarkus needs. (Run `mise trust` the first time.) Without mise, point `GRAALVM_HOME` at a Mandrel or GraalVM install.

CI reads the Mandrel version from `mise.toml` too, and checks each download against a SHA-256 pinned in `.github/workflows/ci.yml`. When you bump the version, update those checksums from the `.sha256` files on the [Mandrel release page](https://github.com/graalvm/mandrel/releases).

`scripts/smoke-test.sh` checks a native build end to end: the native controller launches a grid of native moles, every window must appear, and stopping the controller must close them. It needs an X display and `xwininfo`; on a headless Linux box, run it as `xvfb-run scripts/smoke-test.sh`.

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
- On macOS, moles stay out of the Dock and don't steal focus. Native moles get this from an Info.plist embedded at build time (the `macos` profile in `mole/pom.xml`). JVM moles can't embed one, so each briefly shows a Dock icon and takes focus as it starts.
- Moles float above other windows, and a click on one registers even when another app is active.
- On Linux under Wayland, apps can't position their own windows. Run with `GDK_BACKEND=x11`.

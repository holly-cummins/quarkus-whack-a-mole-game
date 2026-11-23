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

Native builds need [Mandrel](https://github.com/graalvm/mandrel/releases) or GraalVM for JDK 25.0.4 or newer. The easiest way is [mise](https://mise.jdx.dev): `mise.toml` pins Mandrel 25.0.5, so in this directory `mise install` fetches it and sets `JAVA_HOME`, which is all Quarkus needs. (Run `mise trust` the first time.) Without mise, point `GRAALVM_HOME` at a Mandrel or GraalVM install. CI reads the Mandrel version from `mise.toml` too.

`scripts/smoke-test.sh` checks a native build end to end: the native controller launches a grid of native moles, every window must appear, a mole that closes must be replaced, and stopping the controller must close them. It needs an X display and `xwininfo`; on a headless Linux box, run it as `xvfb-run scripts/smoke-test.sh`.

## Run

Run from the repository root, because the controller finds the mole under `mole/target/`:

```
java -jar controller/target/quarkus-app/quarkus-run.jar launch --count 12 --screen-width 1920 --screen-height 1080
```

`--count` is roughly how many slots you want. The controller works out the rows and columns, and rounds the count up to fill the last row, so 7 becomes a 3x3 grid of 9. By default each tile has the screen's aspect ratio. Add `--square` for square tiles, as the video needs.

Add `--sparseness 30` to leave 30% of the slots empty at the start, chosen at random. Whenever a mole closes, a new one opens in a random empty slot, and the slot it left becomes empty. With no empty slots, the new mole takes the old one's place.

One mole is the target. It shows the Quarkus logo, and clicking it closes it; clicks on the other moles do nothing. The target's replacement becomes the new target, so the target jumps to a random empty slot, and it's always a mole that has only just started. Replacements flash as they appear, with rings rippling out from the centre. The entrance only animates a window that is already showing, so it doesn't slow down the replacement.

### Mole apocalypse

Start the controller with the `apocalypse` profile:

```
java -Dquarkus.profile=apocalypse -jar controller/target/quarkus-app/quarkus-run.jar launch
```

(or set `QUARKUS_PROFILE=apocalypse`; for a native controller, put `-Dquarkus.profile=apocalypse` before `launch`). A row of moles appears along the bottom of the screen, and new ones keep coming, filling the lowest gaps first so the moles rise up the screen. Every mole is a target: click them to close them. There's no timer: each new mole is launched as soon as the one before it has painted its window, so the moles come as fast as they can start. When the screen is full, it's game over, with your score. The GAME OVER screen ignores clicks, so frantic clicking doesn't hide your score; press Ctrl-C in the controller's terminal to finish. Its background is very transparent, so the moles show through; set `MOLE_GAME_OVER_BACKGROUND_OPACITY` (a percentage, default 20) to change that. On Linux, the transparency needs a compositing window manager.

Native moles start so fast that the screen fills in about a second and a half. With `--mode JVM` you get several seconds and a fighting chance.

The profile's settings are in `controller/src/main/resources/application.properties` (`%apocalypse.moles.*`). The controller passes the profile on to each mole, so moles have `%apocalypse` settings too, in `mole/src/main/resources/application.properties`; in mole apocalypse, new moles flash red.

### Entrances

The entrance is configurable. Moles inherit the controller's environment, so set environment variables before starting the controller:

| Variable | Default | Meaning |
|---|---|---|
| `MOLE_ENTRANCE_FLASH_COLOUR` | `#FFE066` | Colour the background flashes to, before fading back to brown |
| `MOLE_ENTRANCE_FLASH_MILLIS` | `600` | How long the fade takes; `0` turns the flash off |
| `MOLE_ENTRANCE_RIPPLES_COLOUR` | `#4695EB` | Colour of the rings |
| `MOLE_ENTRANCE_RIPPLES_COUNT` | `3` | How many rings; `0` turns them off |
| `MOLE_ENTRANCE_RIPPLES_MILLIS` | `540` | How long each ring takes to spread out and fade |
| `MOLE_ENTRANCE_RIPPLES_INTERVAL_MILLIS` | `180` | Delay between one ring and the next |
| `MOLE_ENTRANCE_RIPPLES_WIDTH_PERCENT` | `7` | Starting thickness of a ring, as a percentage of the window's shorter side |
| `MOLE_ENTRANCE_FRAME_MILLIS` | `16` | Time between animation frames |

For example, `MOLE_ENTRANCE_RIPPLES_COUNT=6 MOLE_ENTRANCE_FLASH_MILLIS=0 java -jar controller/target/quarkus-app/quarkus-run.jar launch --sparseness 30`.

If a native mole has been built, it's used automatically. Use `--mode JVM` or `--mode NATIVE` to choose. Add `--dry-run` to print the commands instead of running them, and `--help` to see all options.

Stopping the controller (Ctrl-C) closes every mole it started. A mole that exits with an error within a second of launch isn't replaced, so a broken build doesn't relaunch forever.

Notes:
- SWT is not compatible with dev mode on Mac.
- On macOS, JVM moles are started with `-XstartOnFirstThread`; the controller adds this for you.
- On macOS, moles stay out of the Dock and don't steal focus. Native moles get this from an Info.plist embedded at build time (the `macos` profile in `mole/pom.xml`). JVM moles can't embed one, so each briefly shows a Dock icon and takes focus as it starts.
- Moles float above other windows, and a click on one registers even when another app is active.
- On Linux under Wayland, apps can't position their own windows. Run with `GDK_BACKEND=x11`.

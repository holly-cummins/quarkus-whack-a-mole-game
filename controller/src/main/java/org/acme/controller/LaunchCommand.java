package org.acme.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "launch", mixinStandardHelpOptions = true,
        description = "Tile the screen with a grid of moles and wait until they are all closed.")
public class LaunchCommand implements Runnable {

    @Parameters(paramLabel = "COUNT", description = "How many moles to launch. The rows and columns are worked out to fit.")
    int count;

    @Option(names = "--square", description = "Make the tiles square, as the video needs. "
            + "By default tiles take the screen's aspect ratio.")
    boolean square;

    @Option(names = "--gap", defaultValue = "20", description = "Pixels between tiles (default: ${DEFAULT-VALUE})")
    int gap;

    @Option(names = "--screen-width", defaultValue = "1920", description = "Screen width in pixels (default: ${DEFAULT-VALUE})")
    int screenWidth;

    @Option(names = "--screen-height", defaultValue = "1080", description = "Screen height in pixels (default: ${DEFAULT-VALUE})")
    int screenHeight;

    @Option(names = "--mode", defaultValue = "AUTO",
            description = "How to run moles: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}, which picks native if it has been built)")
    MoleLauncher.Mode mode;

    @Option(names = "--borderless", description = "Remove window decorations, so tiles sit flush")
    boolean borderless;

    @Option(names = "--dry-run", description = "Print the commands instead of running them")
    boolean dryRun;

    @Override
    public void run() {
        double aspectRatio = square ? 1.0 : (double) screenWidth / screenHeight;
        Grid grid = Grid.fit(count, screenWidth, screenHeight, aspectRatio, gap);
        List<Tile> tiles = grid.tiles().subList(0, count);
        MoleLauncher launcher = new MoleLauncher(mode, borderless);

        if (dryRun) {
            tiles.forEach(tile -> System.out.println(String.join(" ", launcher.command(tile, MoleLauncher.nowMicros()))));
            return;
        }

        List<Process> moles = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> moles.forEach(Process::destroy)));

        long start = System.nanoTime();
        for (Tile tile : tiles) {
            try {
                moles.add(launcher.launch(tile));
            } catch (Exception e) {
                System.err.println("Could not launch mole " + tile.id() + ": " + e.getMessage());
            }
        }
        System.out.printf("Spawned %d %s moles in %.1f ms, on a %dx%d grid of %dx%d tiles%n", moles.size(), launcher.mode(),
                (System.nanoTime() - start) / 1e6, grid.columns(), grid.rows(), grid.tileWidth(), grid.tileHeight());

        CompletableFuture.allOf(moles.stream().map(Process::onExit).toArray(CompletableFuture[]::new)).join();
        System.out.println("All moles closed.");
    }
}

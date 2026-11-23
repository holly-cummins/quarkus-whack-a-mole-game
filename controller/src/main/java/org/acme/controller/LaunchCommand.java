package org.acme.controller;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "launch", mixinStandardHelpOptions = true,
        description = "Tile the screen with a grid of moles. Whenever one closes, a new one opens in a random empty slot. "
                + "Stop with Ctrl-C, which closes them all.")
public class LaunchCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Option(names = { "-n", "--count" }, defaultValue = "12",
            description = "Roughly how many slots the grid should have. The rows and columns are worked out to fit, "
                    + "and the count is rounded up to fill the last row (default: ${DEFAULT-VALUE})")
    int count;

    @Option(names = "--sparseness", defaultValue = "0", paramLabel = "PERCENT",
            description = "Percentage of slots to leave empty at the start, as gaps for moles to move into "
                    + "(default: ${DEFAULT-VALUE})")
    int sparseness;

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
        Grid grid;
        Board board;
        try {
            grid = Grid.fit(count, screenWidth, screenHeight, aspectRatio, gap);
            board = new Board(grid.tiles(), sparseness);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        }
        MoleLauncher launcher = new MoleLauncher(mode, borderless);

        if (dryRun) {
            // The real launch picks a random target; a dry run makes the first mole the target, so the output is stable
            Tile target = board.initial().getFirst();
            board.initial().forEach(tile -> System.out.println(
                    String.join(" ", launcher.command(tile, tile.equals(target), false, MoleLauncher.nowMicros()))));
            return;
        }

        Fleet fleet = new Fleet(board, launcher);
        Runtime.getRuntime().addShutdownHook(new Thread(fleet::stop));

        long start = System.nanoTime();
        int launched = fleet.start();
        System.out.printf("Spawned %d %s moles in %.1f ms, on a %dx%d grid of %dx%d tiles with %d empty%n", launched,
                launcher.mode(), (System.nanoTime() - start) / 1e6, grid.columns(), grid.rows(), grid.tileWidth(),
                grid.tileHeight(), board.empty().size());
        try {
            fleet.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

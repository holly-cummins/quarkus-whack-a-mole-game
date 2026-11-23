package org.acme.controller;

import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "launch", mixinStandardHelpOptions = true,
        description = { "Tile the screen with moles and play a game: whack-a-mole by default, or mole apocalypse with "
                + "the apocalypse profile (-Dquarkus.profile=apocalypse or QUARKUS_PROFILE=apocalypse).",
                "Stop with Ctrl-C, which closes every mole." })
public class LaunchCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Inject
    GameConfig config;

    @Inject
    SmallRyeConfig smallRyeConfig;

    @Option(names = { "-n", "--count" },
            description = "Roughly how many slots the grid should have. The rows and columns are worked out to fit, "
                    + "and the count is rounded up to fill the last row (default: moles.count, 12 for whack-a-mole)")
    Integer count;

    @Option(names = "--sparseness", paramLabel = "PERCENT",
            description = "Percentage of slots to leave empty at the start, as gaps for moles to move into "
                    + "(default: moles.sparseness, 0 for whack-a-mole)")
    Integer sparseness;

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

    /** When the first moles were up, for timing the game. */
    private volatile long startedNanos;

    @Option(names = "--dry-run", description = "Print the commands for the first moles instead of running them")
    boolean dryRun;

    @Override
    public void run() {
        Game game = config.game();
        double aspectRatio = square ? 1.0 : (double) screenWidth / screenHeight;
        Grid grid;
        Board board;
        try {
            grid = Grid.fit(count != null ? count : config.count(), screenWidth, screenHeight, aspectRatio, gap);
            board = new Board(grid.tiles(), sparseness != null ? sparseness : config.sparseness(), game.placement());
        } catch (IllegalArgumentException e) {
            throw new ParameterException(spec.commandLine(), e.getMessage());
        }
        MoleLauncher launcher = new MoleLauncher(mode, borderless, smallRyeConfig.getProfiles());

        if (dryRun) {
            // A real launch picks a random target; a dry run makes the first mole the target, so the output is stable
            Tile target = board.initial().getFirst();
            board.initial().forEach(tile -> System.out.println(String.join(" ", launcher.command(tile,
                    game.everyMoleIsATarget() || tile.equals(target), false, MoleLauncher.nowMicros()))));
            return;
        }

        Fleet fleet = new Fleet(board, launcher, game);
        Spawner spawner = new Spawner(fleet, () -> gameOver(fleet));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            spawner.stop();
            fleet.stop();
        }));

        long start = System.nanoTime();
        int launched = fleet.start();
        startedNanos = System.nanoTime();
        System.out.printf("%s: spawned %d %s moles in %.1f ms, on a %dx%d grid of %dx%d tiles with %d empty%n", game,
                launched, launcher.mode(), (startedNanos - start) / 1e6, grid.columns(), grid.rows(), grid.tileWidth(),
                grid.tileHeight(), board.empty().size());
        if (game.spawns()) {
            if (board.isFull()) {
                gameOver(fleet);
            } else {
                spawner.start();
            }
        }
        try {
            fleet.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void gameOver(Fleet fleet) {
        String score = String.format("You whacked %d moles in %.1f seconds", fleet.whacked(),
                (System.nanoTime() - startedNanos) / 1e9);
        System.out.println("Game over! " + score + ". Press Ctrl-C to finish.");
        fleet.gameOver(new Tile(-1, 0, 0, screenWidth, screenHeight), score);
    }
}

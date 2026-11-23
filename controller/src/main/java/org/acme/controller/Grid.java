package org.acme.controller;

import java.util.ArrayList;
import java.util.List;

/**
 * A grid of equally sized tiles, centred on the screen, with a gap between tiles and at least that gap around the
 * edges. The grid may have more slots than were asked for, to fill its last row; the launcher fills them all.
 */
public record Grid(int columns, int rows, int tileWidth, int tileHeight, int left, int top, int gap) {

    /**
     * Fits at least {@code count} tiles of the given aspect ratio (width / height) on the screen, choosing the number
     * of columns that makes the tiles as large as possible. With the screen's own aspect ratio, each tile is a small
     * copy of the screen. With an aspect ratio of 1, the tiles are square, and the grid's shape follows the screen.
     */
    public static Grid fit(int count, int screenWidth, int screenHeight, double aspectRatio, int gap) {
        if (count < 1) {
            throw new IllegalArgumentException("Need at least one tile");
        }
        if (gap < 0) {
            throw new IllegalArgumentException("Gap cannot be negative");
        }
        Grid best = null;
        for (int columns = 1; columns <= count; columns++) {
            int rows = (count + columns - 1) / columns;
            double maxWidth = (screenWidth - gap * (columns + 1.0)) / columns;
            double maxHeight = (screenHeight - gap * (rows + 1.0)) / rows;
            int height = (int) Math.min(maxHeight, maxWidth / aspectRatio);
            int width = (int) Math.min(maxWidth, height * aspectRatio);
            if (width < 1 || height < 1) {
                continue;
            }
            Grid candidate = new Grid(columns, rows, width, height,
                    (screenWidth - columns * width - (columns - 1) * gap) / 2,
                    (screenHeight - rows * height - (rows - 1) * gap) / 2,
                    gap);
            if (best == null || candidate.isBetterThan(best)) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                    "Can't fit " + count + " tiles on a " + screenWidth + "x" + screenHeight + " screen with a gap of " + gap);
        }
        return best;
    }

    /** Bigger tiles win; for equal tiles, fewer empty slots. */
    private boolean isBetterThan(Grid other) {
        long area = (long) tileWidth * tileHeight;
        long otherArea = (long) other.tileWidth * other.tileHeight;
        return area != otherArea ? area > otherArea : slots() < other.slots();
    }

    public int slots() {
        return columns * rows;
    }

    /** Every slot in the grid, numbered row by row from the top left. */
    public List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>(slots());
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                tiles.add(new Tile(tiles.size(),
                        left + col * (tileWidth + gap),
                        top + row * (tileHeight + gap),
                        tileWidth, tileHeight));
            }
        }
        return tiles;
    }
}

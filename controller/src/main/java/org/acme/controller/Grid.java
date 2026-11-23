package org.acme.controller;

import java.util.ArrayList;
import java.util.List;

/** Divides a screen into equally sized tiles, separated by a gap. */
public record Grid(int screenWidth, int screenHeight, int columns, int rows, int gap) {

    public Grid {
        if (columns < 1 || rows < 1) {
            throw new IllegalArgumentException("Grid needs at least one column and one row");
        }
        if (gap < 0) {
            throw new IllegalArgumentException("Gap cannot be negative");
        }
    }

    public List<Tile> tiles() {
        int width = (screenWidth - gap * (columns + 1)) / columns;
        int height = (screenHeight - gap * (rows + 1)) / rows;
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Gap is too large for a " + columns + "x" + rows + " grid");
        }
        List<Tile> tiles = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                tiles.add(new Tile(tiles.size(),
                        gap + col * (width + gap),
                        gap + row * (height + gap),
                        width, height));
            }
        }
        return tiles;
    }
}

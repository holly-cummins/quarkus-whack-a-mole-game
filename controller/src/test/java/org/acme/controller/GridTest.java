package org.acme.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GridTest {

    @Test
    void screenShapedTilesForTwelveOnAWideScreen() {
        Grid grid = Grid.fit(12, 1920, 1080, 1920.0 / 1080, 20);
        assertEquals(4, grid.columns());
        assertEquals(3, grid.rows());
        assertEquals(1920.0 / 1080, (double) grid.tileWidth() / grid.tileHeight(), 0.01);
        assertFitsOnScreen(grid, 1920, 1080);
    }

    @Test
    void screenShapedTilesLeaveSpareSlotsWhenTheCountIsAwkward() {
        Grid grid = Grid.fit(7, 1920, 1080, 1920.0 / 1080, 20);
        assertEquals(9, grid.slots());
        assertFitsOnScreen(grid, 1920, 1080);
    }

    @Test
    void squareTilesFollowTheScreenShape() {
        // 144 squares on a 16:9 screen is a 16x9 grid
        Grid grid = Grid.fit(144, 1920, 1080, 1.0, 0);
        assertEquals(16, grid.columns());
        assertEquals(9, grid.rows());
        assertEquals(120, grid.tileWidth());
        assertEquals(120, grid.tileHeight());
    }

    @Test
    void squareTilesWithoutGapsSitFlush() {
        Grid grid = Grid.fit(12, 400, 300, 1.0, 0);
        List<Tile> tiles = grid.tiles();
        assertEquals(new Tile(0, 0, 0, 100, 100), tiles.get(0));
        assertEquals(new Tile(1, 100, 0, 100, 100), tiles.get(1));
        assertEquals(new Tile(4, 0, 100, 100, 100), tiles.get(4));
    }

    @Test
    void gridIsCentredOnTheScreen() {
        Grid grid = Grid.fit(2, 1000, 400, 1.0, 0);
        // two 400px squares side by side, with 100px either side
        assertEquals(List.of(new Tile(0, 100, 0, 400, 400), new Tile(1, 500, 0, 400, 400)), grid.tiles());
    }

    @Test
    void rejectsImpossibleRequests() {
        assertThrows(IllegalArgumentException.class, () -> Grid.fit(0, 1920, 1080, 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> Grid.fit(4, 100, 100, 1.0, 60));
    }

    private static void assertFitsOnScreen(Grid grid, int screenWidth, int screenHeight) {
        for (Tile tile : grid.tiles()) {
            assertTrue(tile.x() >= grid.gap() && tile.y() >= grid.gap(), tile.toString());
            assertTrue(tile.x() + tile.width() <= screenWidth - grid.gap(), tile.toString());
            assertTrue(tile.y() + tile.height() <= screenHeight - grid.gap(), tile.toString());
        }
    }
}

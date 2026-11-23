package org.acme.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class GridTest {

    @Test
    void tilesFillTheScreenWithGaps() {
        List<Tile> tiles = new Grid(100, 50, 2, 1, 10).tiles();
        assertEquals(List.of(new Tile(0, 10, 10, 35, 30), new Tile(1, 55, 10, 35, 30)), tiles);
    }

    @Test
    void tilesAreNumberedRowByRow() {
        List<Tile> tiles = new Grid(300, 200, 3, 2, 0).tiles();
        assertEquals(6, tiles.size());
        assertEquals(new Tile(4, 100, 100, 100, 100), tiles.get(4));
    }

    @Test
    void rejectsGapsThatLeaveNoRoom() {
        assertThrows(IllegalArgumentException.class, () -> new Grid(100, 100, 5, 5, 30).tiles());
    }
}

package org.acme.controller;

import static org.acme.controller.Board.Placement.BOTTOM_UP;
import static org.acme.controller.Board.Placement.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class BoardTest {

    private static final List<Tile> SLOTS = new Grid(4, 3, 100, 100, 0, 0, 0).tiles();

    @Test
    void withNoSparsenessEverySlotHasAMole() {
        Board board = new Board(SLOTS, 0, RANDOM, new Random(1));
        assertEquals(SLOTS, board.initial());
        assertTrue(board.empty().isEmpty());
    }

    @Test
    void aFullBoardPutsTheReplacementBackInTheSameSlot() {
        Board board = new Board(SLOTS, 0, RANDOM, new Random(1));
        assertEquals(SLOTS.get(5), board.move(SLOTS.get(5)));
    }

    @Test
    void sparsenessLeavesThatPercentageOfSlotsEmpty() {
        Board board = new Board(SLOTS, 25, RANDOM, new Random(1));
        assertEquals(9, board.initial().size());
        assertEquals(3, board.empty().size());
        assertPartition(board.initial(), board.empty());
    }

    @Test
    void atLeastOneMoleSurvivesTotalSparseness() {
        Board board = new Board(SLOTS, 100, RANDOM, new Random(1));
        assertEquals(1, board.initial().size());
    }

    @Test
    void aReplacementMovesToAnEmptySlotAndFreesTheOldOne() {
        Board board = new Board(SLOTS, 50, RANDOM, new Random(1));
        Tile vacated = board.initial().getFirst();
        List<Tile> emptyBefore = board.empty();

        Tile next = board.move(vacated);

        assertNotEquals(vacated, next);
        assertTrue(emptyBefore.contains(next));
        assertTrue(board.empty().contains(vacated));
        assertEquals(emptyBefore.size(), board.empty().size());
    }

    @Test
    void rejectsSparsenessOutsideAPercentage() {
        assertThrows(IllegalArgumentException.class, () -> new Board(SLOTS, -1, RANDOM));
        assertThrows(IllegalArgumentException.class, () -> new Board(SLOTS, 101, RANDOM));
    }

    @Test
    void concurrentMovesNeverPutTwoMolesInOneSlot() throws Exception {
        List<Tile> slots = new Grid(20, 10, 10, 10, 0, 0, 0).tiles();
        Board board = new Board(slots, 50, RANDOM);
        List<Tile> initial = board.initial();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Tile>> moles = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Tile start : initial) {
                moles.add(pool.submit(() -> {
                    go.await();
                    Tile tile = start;
                    for (int i = 0; i < 1000; i++) {
                        tile = board.move(tile);
                    }
                    return tile;
                }));
            }
            go.countDown();
            List<Tile> occupied = new ArrayList<>();
            for (Future<Tile> mole : moles) {
                occupied.add(mole.get());
            }
            assertPartition(occupied, board.empty());
            assertEquals(slots.size(), occupied.size() + board.empty().size());
        }
    }

    /** Every slot is either occupied or empty, never both, and none is lost. */
    private static void assertPartition(List<Tile> occupied, List<Tile> empty) {
        Set<Tile> all = new HashSet<>(occupied);
        assertEquals(occupied.size(), all.size(), "two moles share a slot");
        all.addAll(empty);
        assertEquals(occupied.size() + empty.size(), all.size(), "a slot is both occupied and empty");
    }

    @Test
    void bottomUpStartsWithTheBottomRows() {
        // 12 slots in 3 rows of 4; 50% sparseness leaves 6 moles: the bottom row, and 2 in the middle row
        Board board = new Board(SLOTS, 50, BOTTOM_UP, new Random(1));
        List<Tile> initial = board.initial();
        assertEquals(6, initial.size());
        assertEquals(4, initial.stream().filter(tile -> tile.y() == 200).count());
        assertEquals(2, initial.stream().filter(tile -> tile.y() == 100).count());
        assertEquals(200, initial.getFirst().y(), "the bottom row appears first");
    }

    @Test
    void bottomUpFillsTheLowestGapFirst() {
        Board board = new Board(SLOTS, 50, BOTTOM_UP, new Random(1));
        assertEquals(100, board.claim().y());
        assertEquals(100, board.claim().y());
        // The middle row is full now, so the top row is next
        assertEquals(0, board.claim().y());
        Tile bottom = board.initial().getFirst();
        board.vacate(bottom);
        assertEquals(bottom, board.claim(), "a gap in a lower row is filled before the top row");
    }

    @Test
    void claimReturnsNullWhenTheBoardIsFull() {
        Board board = new Board(SLOTS, 0, BOTTOM_UP, new Random(1));
        assertTrue(board.isFull());
        assertNull(board.claim());
    }
}

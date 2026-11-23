package org.acme.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        Board board = new Board(SLOTS, 0, new Random(1));
        assertEquals(SLOTS, board.initial());
        assertTrue(board.empty().isEmpty());
    }

    @Test
    void aFullBoardPutsTheReplacementBackInTheSameSlot() {
        Board board = new Board(SLOTS, 0, new Random(1));
        assertEquals(SLOTS.get(5), board.move(SLOTS.get(5)));
    }

    @Test
    void sparsenessLeavesThatPercentageOfSlotsEmpty() {
        Board board = new Board(SLOTS, 25, new Random(1));
        assertEquals(9, board.initial().size());
        assertEquals(3, board.empty().size());
        assertPartition(board.initial(), board.empty());
    }

    @Test
    void atLeastOneMoleSurvivesTotalSparseness() {
        Board board = new Board(SLOTS, 100, new Random(1));
        assertEquals(1, board.initial().size());
    }

    @Test
    void aReplacementMovesToAnEmptySlotAndFreesTheOldOne() {
        Board board = new Board(SLOTS, 50, new Random(1));
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
        assertThrows(IllegalArgumentException.class, () -> new Board(SLOTS, -1));
        assertThrows(IllegalArgumentException.class, () -> new Board(SLOTS, 101));
    }

    @Test
    void concurrentMovesNeverPutTwoMolesInOneSlot() throws Exception {
        List<Tile> slots = new Grid(20, 10, 10, 10, 0, 0, 0).tiles();
        Board board = new Board(slots, 50);
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
}

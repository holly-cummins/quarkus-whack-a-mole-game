package org.acme.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Which slots of the grid have a mole in them. The board keeps a list of the empty slots, and when a mole closes it
 * hands back a random empty slot for the replacement.
 * <p>
 * Thread-safe: moles close on whichever thread notices their process has exited, so every method that reads or
 * changes the empty slots holds the board's lock.
 */
public class Board {

    private final List<Tile> occupied;
    private final List<Tile> empty;
    private final RandomGenerator random;

    /**
     * @param slots      every slot in the grid
     * @param sparseness the percentage of slots to leave empty at the start, from 0 to 100. At least one slot always
     *                   gets a mole.
     */
    public Board(List<Tile> slots, int sparseness) {
        this(slots, sparseness, new Random());
    }

    Board(List<Tile> slots, int sparseness, RandomGenerator random) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Need at least one slot");
        }
        if (sparseness < 0 || sparseness > 100) {
            throw new IllegalArgumentException("Sparseness is a percentage, from 0 to 100, not " + sparseness);
        }
        this.random = random;
        int emptyCount = (int) Math.min(slots.size() - 1, Math.round(slots.size() * sparseness / 100.0));
        List<Tile> shuffled = new ArrayList<>(slots);
        Collections.shuffle(shuffled, random);
        empty = new ArrayList<>(shuffled.subList(0, emptyCount));
        List<Tile> initial = new ArrayList<>(shuffled.subList(emptyCount, shuffled.size()));
        initial.sort((a, b) -> Integer.compare(a.id(), b.id()));
        occupied = List.copyOf(initial);
    }

    /** The slots that get a mole at the start, in grid order. */
    public List<Tile> initial() {
        return occupied;
    }

    /** A snapshot of the slots that are empty now. */
    public synchronized List<Tile> empty() {
        return List.copyOf(empty);
    }

    /**
     * A mole has left the given slot: picks a random empty slot for its replacement and marks the old slot empty.
     * The replacement only goes back into the same slot when no other slot is free.
     */
    public synchronized Tile move(Tile vacated) {
        if (empty.isEmpty()) {
            return vacated;
        }
        // Swap the chosen slot to the end, so removing it doesn't shift the rest of the list
        int chosen = random.nextInt(empty.size());
        Collections.swap(empty, chosen, empty.size() - 1);
        Tile next = empty.removeLast();
        empty.add(vacated);
        return next;
    }

    /** Marks a slot empty without filling another, e.g. when a mole couldn't be launched. */
    public synchronized void vacate(Tile tile) {
        empty.add(tile);
    }
}

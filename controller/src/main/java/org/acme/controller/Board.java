package org.acme.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Which slots of the grid have a mole in them. The board keeps a list of the empty slots, and hands them out for new
 * moles according to its {@link Placement}.
 * <p>
 * Thread-safe: moles close on whichever thread notices their process has exited, so every method that reads or
 * changes the empty slots holds the board's lock.
 */
public class Board {

    /** Where new moles go. */
    public enum Placement {
        /** Anywhere: the first moles take random slots, and each new mole takes a random empty slot. */
        RANDOM,
        /**
         * From the bottom up: the first moles fill the bottom rows, and each new mole takes a random empty slot in the
         * lowest row that has one, so the moles rise up the screen.
         */
        BOTTOM_UP
    }

    private static final Comparator<Tile> BOTTOM_FIRST = Comparator.comparingInt(Tile::y).reversed();

    private final Placement placement;
    private final List<Tile> initial;
    private final List<Tile> empty;
    private final RandomGenerator random;

    /**
     * @param slots      every slot in the grid
     * @param sparseness the percentage of slots to leave empty at the start, from 0 to 100. At least one slot always
     *                   gets a mole.
     */
    public Board(List<Tile> slots, int sparseness, Placement placement) {
        this(slots, sparseness, placement, new Random());
    }

    Board(List<Tile> slots, int sparseness, Placement placement, RandomGenerator random) {
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Need at least one slot");
        }
        if (sparseness < 0 || sparseness > 100) {
            throw new IllegalArgumentException("Sparseness is a percentage, from 0 to 100, not " + sparseness);
        }
        this.placement = placement;
        this.random = random;
        int filled = (int) Math.max(1, slots.size() - Math.round(slots.size() * sparseness / 100.0));
        List<Tile> order = new ArrayList<>(slots);
        Collections.shuffle(order, random);
        if (placement == Placement.BOTTOM_UP) {
            // A stable sort, so the shuffle still decides which columns of a part-filled row get moles
            order.sort(BOTTOM_FIRST);
        }
        List<Tile> first = new ArrayList<>(order.subList(0, filled));
        first.sort(placement == Placement.BOTTOM_UP
                ? BOTTOM_FIRST.thenComparingInt(Tile::x)
                : Comparator.comparingInt(Tile::id));
        initial = List.copyOf(first);
        empty = new ArrayList<>(order.subList(filled, order.size()));
    }

    /** The slots that get a mole at the start, in the order they should appear. */
    public List<Tile> initial() {
        return initial;
    }

    /** A snapshot of the slots that are empty now. */
    public synchronized List<Tile> empty() {
        return List.copyOf(empty);
    }

    public synchronized boolean isFull() {
        return empty.isEmpty();
    }

    /**
     * A mole has left the given slot: picks an empty slot for its replacement and marks the old slot empty.
     * The replacement only goes back into the same slot when no other slot is free.
     */
    public synchronized Tile move(Tile vacated) {
        if (empty.isEmpty()) {
            return vacated;
        }
        Tile next = take();
        empty.add(vacated);
        return next;
    }

    /** Takes an empty slot for a new mole, or returns null if the board is full. */
    public synchronized Tile claim() {
        return empty.isEmpty() ? null : take();
    }

    /** Marks a slot empty without filling another, e.g. when a mole closes and isn't replaced. */
    public synchronized void vacate(Tile tile) {
        empty.add(tile);
    }

    private Tile take() {
        List<Integer> candidates = new ArrayList<>();
        int lowestRow = empty.stream().mapToInt(Tile::y).max().orElseThrow();
        for (int i = 0; i < empty.size(); i++) {
            if (placement == Placement.RANDOM || empty.get(i).y() == lowestRow) {
                candidates.add(i);
            }
        }
        int chosen = candidates.get(random.nextInt(candidates.size()));
        // Swap the chosen slot to the end, so removing it doesn't shift the rest of the list
        Collections.swap(empty, chosen, empty.size() - 1);
        return empty.removeLast();
    }
}

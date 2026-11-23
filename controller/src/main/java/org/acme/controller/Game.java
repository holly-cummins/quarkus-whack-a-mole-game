package org.acme.controller;

/** The rules of each game the moles can play. */
public enum Game {

    /**
     * One mole at a time is the target. When the target is hit, a replacement target pops up in a random empty slot,
     * so the number of moles stays the same.
     */
    WHACK_A_MOLE(false, true, Board.Placement.RANDOM),

    /**
     * Every mole is a target, and hitting one leaves a gap. New moles keep appearing as fast as they can start, filling
     * the screen from the bottom up; the game is over when the screen is full.
     */
    APOCALYPSE(true, false, Board.Placement.BOTTOM_UP);

    private final boolean everyMoleIsATarget;
    private final boolean replacesClosedMoles;
    private final Board.Placement placement;

    Game(boolean everyMoleIsATarget, boolean replacesClosedMoles, Board.Placement placement) {
        this.everyMoleIsATarget = everyMoleIsATarget;
        this.replacesClosedMoles = replacesClosedMoles;
        this.placement = placement;
    }

    public boolean everyMoleIsATarget() {
        return everyMoleIsATarget;
    }

    public boolean replacesClosedMoles() {
        return replacesClosedMoles;
    }

    /** Whether new moles keep appearing on a timer, rather than only to replace moles that close. */
    public boolean spawns() {
        return !replacesClosedMoles;
    }

    public Board.Placement placement() {
        return placement;
    }
}

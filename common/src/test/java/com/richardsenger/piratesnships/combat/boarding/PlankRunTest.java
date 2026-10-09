package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.combat.boarding.PlankRun.Cell;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Failure;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Ground;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Landing;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Result;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.DOWN;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.DOWN_HIGH;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.DOWN_LOW;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.FLAT;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.UP;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.UP_HIGH;
import static com.richardsenger.piratesnships.combat.boarding.PlankRun.Pitch.UP_LOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlankRunTest {

    private static final Settings DEFAULTS = new Settings(6, 2, 2, true);

    /**
     * A side view of the run's line: column {@code i} out from the start, {@code dy} blocks above the first cell. Blocks
     * are {@link Ground} values (anything not set is empty); {@code taken} cells are not free but have no collision (a
     * flower, the start ship's own air the plot does not take).
     */
    private static final class Grid implements PlankRun.Probe {
        final Map<Long, Ground> blocks = new HashMap<>();
        final Set<Long> taken = new HashSet<>();

        private static long key(int i, int dy) {
            return ((long) i << 32) | (dy & 0xffffffffL);
        }

        /** Fills columns {@code i0..i1} with {@code g} from {@code dy0} up to {@code dy1}. */
        Grid fill(int i0, int i1, int dy0, int dy1, Ground g) {
            for (int i = i0; i <= i1; i++) for (int dy = dy0; dy <= dy1; dy++) blocks.put(key(i, dy), g);
            return this;
        }

        /** A deck (other body) whose top is {@code top} blocks above the first cell's floor, under columns {@code i0..i1}. */
        Grid deck(int i0, int i1, int top) {
            return fill(i0, i1, top - 3, top - 1, Ground.STURDY);
        }

        Grid take(int i, int dy) {
            taken.add(key(i, dy));
            return this;
        }

        @Override
        public boolean free(int i, int dy) {
            return ground(i, dy) == Ground.NONE && !taken.contains(key(i, dy));
        }

        @Override
        public boolean open(int i, int dy) {
            return ground(i, dy) == Ground.NONE;
        }

        @Override
        public Ground ground(int i, int dy) {
            return blocks.getOrDefault(key(i, dy), Ground.NONE);
        }
    }

    private static List<Pitch> pitches(Result r) {
        List<Pitch> out = new ArrayList<>();
        for (Cell c : r.cells()) out.add(c.pitch());
        return out;
    }

    private static List<Integer> floors(Result r) {
        List<Integer> out = new ArrayList<>();
        for (Cell c : r.cells()) out.add(c.dy());
        return out;
    }

    // ------------------------------------------------------------------ start

    @Test
    void aSideFaceStartsBesideItAndRunsOutward() {
        BlockPos clicked = new BlockPos(10, 64, -3);
        PlankRun.Start s = PlankRun.start(clicked, Direction.EAST, Direction.NORTH);
        assertEquals(new BlockPos(11, 64, -3), s.first());
        assertEquals(Direction.EAST, s.direction(), "the player's facing does not matter on a side face");
        assertEquals(new BlockPos(14, 64, -3), s.cell(3));
        assertEquals(new BlockPos(13, 66, -3), s.cell(2, 2));
    }

    @Test
    void theTopFaceStartsOneUpAndOneOutInThePlayersFacing() {
        PlankRun.Start s = PlankRun.start(new BlockPos(0, 10, 0), Direction.UP, Direction.SOUTH);
        assertEquals(new BlockPos(0, 11, 1), s.first());
        assertEquals(Direction.SOUTH, s.direction());
    }

    @Test
    void theBottomFaceLaysNothing() {
        assertNull(PlankRun.start(BlockPos.ZERO, Direction.DOWN, Direction.EAST));
    }

    // ------------------------------------------------------------------ pitches and profiles

    @Test
    void walkingACellBackwardsSwapsClimbAndFall() {
        for (Pitch p : Pitch.values()) {
            assertEquals(p, p.reversed().reversed());
            assertEquals(p.near, p.reversed().far);
            assertEquals(p.far, p.reversed().near);
        }
        assertEquals(DOWN_LOW, UP_LOW.reversed());
        assertEquals(DOWN, UP.reversed());
    }

    @Test
    void aProfileKeepsTheWalkingSurfaceContinuous() {
        assertEquals(List.of(0, 0, 0, 1), floors(new Result(PlankRun.profile(List.of(FLAT, UP_LOW, UP_HIGH, FLAT)), Landing.LEVEL, 0, 0, Failure.NONE, -1)));
        assertEquals(List.of(-1, -1, -1), floors(new Result(PlankRun.profile(List.of(DOWN_HIGH, DOWN_LOW, FLAT)), Landing.LEVEL, 0, 0, Failure.NONE, -1)));
        assertEquals(List.of(0, 1, 2), floors(new Result(PlankRun.profile(List.of(UP, UP, FLAT)), Landing.LEVEL, 0, 0, Failure.NONE, -1)));
        assertEquals(List.of(-1, -2), floors(new Result(PlankRun.profile(List.of(DOWN, DOWN)), Landing.LEVEL, 0, 0, Failure.NONE, -1)));
        assertNull(PlankRun.profile(List.of(UP_LOW, FLAT)), "half a ramp does not join a flat cell");
    }

    @Test
    void everyRampWalksWithoutAJump() {
        // the steps of each cell, then the next cell, in absolute pixels: never more than 6 px up (vanilla steps 0.6 blocks)
        for (List<Pitch> run : List.of(List.of(FLAT, UP_LOW, UP_HIGH, UP_LOW, UP_HIGH, FLAT), List.of(FLAT, UP, UP, FLAT),
                List.of(FLAT, DOWN_HIGH, DOWN_LOW, FLAT), List.of(FLAT, DOWN, DOWN, FLAT))) {
            List<Cell> cells = PlankRun.profile(run);
            double last = 2.0; // the flat board before the run
            for (Cell c : cells) {
                for (double[] step : PlankRun.steps(c.pitch(), 2, 4)) {
                    double top = c.dy() * 16 + step[3];
                    assertTrue(top - last <= 6.0 + 1e-9, run + ": a step of " + (top - last) + " px up");
                    assertTrue(step[3] <= 16.0 && step[2] >= 0.0 && step[2] < step[3], "a step leaves its cell");
                    last = top;
                }
            }
        }
    }

    // ------------------------------------------------------------------ level runs (BRD1)

    @Test
    void aLevelRunEndsAtTheFirstDeck() {
        Grid g = new Grid().deck(3, 5, 0);
        Result r = PlankRun.find(DEFAULTS, g);
        assertTrue(r.found());
        assertEquals(List.of(FLAT, FLAT, FLAT, FLAT), pitches(r));
        assertEquals(Landing.LEVEL, r.landing());
        assertEquals(3, r.landI());
        assertEquals(-1, r.landDy());
    }

    @Test
    void aShipTouchingTheRailTakesAOneCellPlank() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(0, 3, 0));
        assertEquals(1, r.length());
        assertEquals(Landing.LEVEL, r.landing());
    }

    @Test
    void maxLengthIsClampedToTheSegmentCount() {
        assertEquals(6, new Settings(9, 2, 2, true).maxLength());
        assertEquals(1, new Settings(0, 2, 2, true).maxLength());
        assertTrue(PlankRun.find(new Settings(9, 0, 2, true), new Grid().deck(5, 9, 0)).found());
        assertFalse(PlankRun.find(new Settings(9, 0, 2, true), new Grid().deck(6, 9, 0)).found());
    }

    // ------------------------------------------------------------------ slopes (BRD2)

    @Test
    void aDeckOneBlockUpIsClimbedOnAHalfRampMeetingItsEdge() {
        // B's deck one higher, its side three cells out (the old STEP_UP case): a 1-in-2 ramp meets the deck's edge
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 1));
        assertEquals(List.of(FLAT, UP_LOW, UP_HIGH), pitches(r));
        assertEquals(Landing.RAMP, r.landing());
        assertEquals(1.0, r.rise());
    }

    @Test
    void aDeckTwoBlocksUpTakesTwoBlocksOfHalfRamp() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(4, 7, 2));
        assertEquals(List.of(UP_LOW, UP_HIGH, UP_LOW, UP_HIGH), pitches(r));
        assertEquals(List.of(0, 0, 1, 1), floors(r));
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void aDeckOneBlockDownIsReachedOnAFallingHalfRamp() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(2, 6, -1));
        assertEquals(List.of(FLAT, DOWN_HIGH, DOWN_LOW), pitches(r));
        assertEquals(List.of(0, -1, -1), floors(r));
        assertEquals(Landing.LEVEL, r.landing());
        assertEquals(-1.0, r.rise());
    }

    @Test
    void aShortGapTakesA45DegreeCell() {
        // the pier's side right after the first cell: no room for a half ramp
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(1, 4, 1));
        assertEquals(List.of(UP), pitches(r));
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void withoutTheSteepFallbackAShortGapEndsAgainstTheDeck() {
        Result r = PlankRun.find(new Settings(6, 2, 2, false), new Grid().deck(1, 4, 1));
        assertEquals(List.of(FLAT), pitches(r));
        assertEquals(Landing.STEP_UP, r.landing());
    }

    @Test
    void riseRunOneLaysOnly45DegreeRamps() {
        Result r = PlankRun.find(new Settings(6, 2, 1, true), new Grid().deck(3, 6, 1));
        assertEquals(List.of(FLAT, FLAT, UP), pitches(r));
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void maxRiseZeroLaysLevelPlanksOnly() {
        Result r = PlankRun.find(new Settings(6, 0, 2, true), new Grid().deck(3, 6, 1));
        assertEquals(List.of(FLAT, FLAT, FLAT), pitches(r));
        assertEquals(Landing.STEP_UP, r.landing());
        assertEquals(Landing.STEP_DOWN, PlankRun.find(new Settings(6, 0, 2, true), new Grid().deck(2, 6, -1)).landing());
    }

    @Test
    void theShortestRunWinsOverALevelOne() {
        // a block one up two cells out and a level deck four cells out: the ramp onto the near block is shorter
        Grid g = new Grid().deck(4, 7, 0).fill(2, 2, 0, 0, Ground.STURDY);
        Result r = PlankRun.find(DEFAULTS, g);
        assertEquals(List.of(UP_LOW, UP_HIGH), pitches(r));
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void aTallRampCellNeedsHeadroom() {
        // a beam over the last cell before a deck one up: no ramp has room to climb, so the plank ends against the deck
        Grid g = new Grid().deck(3, 6, 1).fill(2, 2, 1, 1, Ground.SOLID);
        Result r = PlankRun.find(DEFAULTS, g);
        assertEquals(List.of(FLAT, FLAT, FLAT), pitches(r));
        assertEquals(Landing.STEP_UP, r.landing());
    }

    // ------------------------------------------------------------------ home ground

    @Test
    void fromAPierTheWorldIsALandingOnlyAcrossAGap() {
        Grid across = new Grid().fill(2, 5, -3, -1, Ground.HOME);
        Result r = PlankRun.find(DEFAULTS, across);
        assertEquals(3, r.length());
        assertEquals(Landing.LEVEL, r.landing());
        assertFalse(PlankRun.find(DEFAULTS, new Grid().fill(0, 5, -3, -1, Ground.HOME)).found(), "along a pier");
        assertEquals(Failure.NO_START, PlankRun.find(DEFAULTS, new Grid().fill(0, 5, -3, -1, Ground.HOME)).failure());
        Grid oneOut = new Grid().fill(1, 5, -3, -1, Ground.HOME);
        assertEquals(2, PlankRun.find(DEFAULTS, oneOut).length(), "a one-block gap takes two cells");
    }

    @Test
    void aShipAtThePierEdgeTakesAOneCellPlankFromThePier() {
        assertEquals(1, PlankRun.find(DEFAULTS, new Grid().deck(0, 4, 0)).length());
    }

    // ------------------------------------------------------------------ refusals

    @Test
    void aTakenCellInTheLevelLineIsBlockedThere() {
        Grid g = new Grid().deck(4, 7, 0).fill(1, 1, 0, 0, Ground.SOLID);
        Result r = PlankRun.find(DEFAULTS, g);
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(1, r.blockedAt());
    }

    @Test
    void aDeckPastTheReachIsTooFar() {
        assertEquals(Failure.TOO_FAR, PlankRun.find(DEFAULTS, new Grid().deck(7, 9, 0)).failure());
        assertEquals(Failure.TOO_FAR, PlankRun.find(DEFAULTS, new Grid().deck(8, 9, 1)).failure(), "one up, past the reach");
        assertEquals(Failure.TOO_FAR, PlankRun.find(new Settings(2, 2, 2, true), new Grid().deck(3, 9, 0)).failure());
    }

    @Test
    void aDeckFarAboveOrBelowIsTooSteep() {
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 4)).failure(), "a quay four up");
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(1, 6, -3)).failure(), "a deck three down");
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(1, 6, 2)).failure(),
                "two up right after the first cell: no ramp fits");
    }

    @Test
    void openWaterHasNothingToLandOn() {
        assertEquals(Failure.NOTHING_TO_LAND, PlankRun.find(DEFAULTS, new Grid()).failure());
        assertEquals(Failure.NOTHING_TO_LAND, PlankRun.find(DEFAULTS, new Grid().fill(0, 9, -6, -4, Ground.SOLID)).failure(),
                "deep water far below");
    }

    @Test
    void anObstacleWithoutASturdyTopIsNoLanding() {
        // a fence post: in the way, nothing to stand on
        Grid g = new Grid().fill(2, 2, -1, 0, Ground.SOLID);
        Result r = PlankRun.find(DEFAULTS, g);
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(2, r.blockedAt());
    }

    // ------------------------------------------------------------------ tip and break

    @Test
    void onlyTheLastSegmentIsTheTip() {
        assertTrue(PlankRun.isTip(5, 6));
        assertFalse(PlankRun.isTip(2, 4));
        assertTrue(PlankRun.isTip(0, 1));
    }

    @Test
    void theRunBreaksOnSidewaysDriftOrAnEndGone() {
        assertFalse(PlankRun.breaks(true, true, 0.0, 0.0, 1.5, 2.5));
        assertFalse(PlankRun.breaks(true, true, 1.5, 0.0, 1.5, 2.5), "exactly at the limit holds");
        assertTrue(PlankRun.breaks(true, true, 1.51, 0.0, 1.5, 2.5));
        assertTrue(PlankRun.breaks(false, true, 0.0, 0.0, 1.5, 2.5), "a ship at an end is gone");
        assertTrue(PlankRun.breaks(true, false, 0.0, 0.0, 1.5, 2.5), "what an end rests on is gone");
        assertTrue(PlankRun.breaks(true, true, Double.NaN, 0.0, 1.5, 2.5), "an unknown distance breaks");
    }

    @Test
    void theHeaveOfAMooredShipIsAllowedUpAndDown() {
        // WAV4: a hull heaves about 1.3 blocks in a storm
        assertFalse(PlankRun.breaks(true, true, 0.2, 1.3, 1.5, 2.5));
        assertFalse(PlankRun.breaks(true, true, 0.2, -1.3, 1.5, 2.5));
        assertFalse(PlankRun.breaks(true, true, 0.0, 2.5, 1.5, 2.5));
        assertTrue(PlankRun.breaks(true, true, 0.0, 2.6, 1.5, 2.5));
        assertTrue(PlankRun.breaks(true, true, 0.0, -2.6, 1.5, 2.5));
        assertTrue(PlankRun.breaks(true, true, 1.6, 1.3, 1.5, 2.5), "the same drift sideways breaks");
        assertTrue(PlankRun.breaks(true, true, 0.0, Double.NaN, 1.5, 2.5));
    }
}

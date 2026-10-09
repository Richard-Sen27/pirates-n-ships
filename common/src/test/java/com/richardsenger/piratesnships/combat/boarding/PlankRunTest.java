package com.richardsenger.piratesnships.combat.boarding;

import com.richardsenger.piratesnships.combat.boarding.PlankRun.Cell;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Failure;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Ground;
import com.richardsenger.piratesnships.combat.boarding.PlankRun.Landing;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlankRunTest {

    private static final Settings DEFAULTS = new Settings(6, 45.0);
    private static final double EPS = 1.0e-9;

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

    private static List<Integer> floors(Result r) {
        List<Integer> out = new ArrayList<>();
        for (Cell c : r.cells()) out.add(c.dy());
        return out;
    }

    /** Every cell's underside lies on the one straight line from the start to the landing. */
    private static void assertStraight(Result r) {
        double slope = r.rise() / (double) r.columns();
        for (Cell c : r.cells()) {
            assertEquals(slope * c.i(), c.dy() + c.near(), EPS, "near edge of " + c);
            assertEquals(slope * (c.i() + 1), c.dy() + c.far(), EPS, "far edge of " + c);
        }
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

    /** RAIL1: a click on a railing port's top facing outboard counts as a click on its outboard side. */
    @Test
    void aClickOnAPortsSillFacingOutboardStartsBeyondThePortAtItsHeight() {
        BlockPos port = new BlockPos(4, 9, 2);
        Direction face = PlankRun.throughOpening(Direction.UP, Direction.EAST, Direction.EAST);
        assertEquals(Direction.EAST, face);
        PlankRun.Start start = PlankRun.start(port, face, Direction.EAST);
        assertEquals(new BlockPos(5, 9, 2), start.first(), "the block beyond the port, at its height");
        assertEquals(Direction.EAST, start.direction());
        assertEquals(Direction.UP, PlankRun.throughOpening(Direction.UP, Direction.NORTH, Direction.EAST), "facing along the rail");
        assertEquals(Direction.UP, PlankRun.throughOpening(Direction.UP, Direction.EAST, null), "no opening");
        assertEquals(Direction.WEST, PlankRun.throughOpening(Direction.WEST, Direction.WEST, Direction.EAST), "a side click keeps its face");
    }

    // ------------------------------------------------------------------ the straight board's cells

    @Test
    void aLevelBoardIsOneFlatCellPerColumn() {
        List<Cell> cells = PlankRun.cells(4, 0);
        assertEquals(4, cells.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(new Cell(i, 0, 0.0, 0.0), cells.get(i));
        }
    }

    @Test
    void twoDownOverSixIsOnePitchWithItsHeightInEveryCell() {
        List<Cell> cells = PlankRun.cells(6, -2);
        assertEquals(6, cells.size(), "the line crosses a block boundary only at column edges");
        int[] floors = {-1, -1, -1, -2, -2, -2};
        for (int i = 0; i < 6; i++) {
            Cell c = cells.get(i);
            assertEquals(i, c.i());
            assertEquals(floors[i], c.dy(), "floor of column " + i);
            assertEquals(-1.0 / 3.0, c.far() - c.near(), EPS, "one pitch: a third of a block down per column");
            assertEquals(-i / 3.0, c.dy() + c.near(), EPS);
        }
        assertEquals(1.0, cells.get(0).near(), EPS, "the board leaves the start at the top of the cell below it");
        assertEquals(0.0, cells.get(5).far(), EPS, "and lands on the floor of the last");
    }

    @Test
    void aBoardCrossingABlockBoundaryInsideAColumnTakesBothCells() {
        // two up over three: column 1 runs from 2/3 to 4/3
        List<Cell> cells = PlankRun.cells(3, 2);
        assertEquals(List.of(0, 0, 1, 1), cells.stream().map(Cell::dy).toList());
        assertEquals(List.of(0, 1, 1, 2), cells.stream().map(Cell::i).toList());
        Cell upper = cells.get(2);
        assertEquals(2.0 / 3.0 - 1.0, upper.near(), EPS, "the upper cell sees the board come in through its floor");
        assertEquals(1.0 / 3.0, upper.far(), EPS);
        // falling, the board reaches the upper cell first
        assertEquals(List.of(-1, -1, -2, -2), PlankRun.cells(3, -2).stream().map(Cell::dy).toList());
        assertEquals(List.of(0, 1, 1, 2), PlankRun.cells(3, -2).stream().map(Cell::i).toList());
    }

    @Test
    void aCellSeenFromTheOtherEndSwapsItsHeights() {
        Cell c = new Cell(2, -1, 0.25, 0.75);
        assertEquals(new Cell(2, -1, 0.75, 0.25), c.reversed());
        assertEquals(c, c.reversed().reversed());
    }

    // ------------------------------------------------------------------ landings

    @Test
    void aLevelRunEndsOnTheFirstDeck() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(3, 5, 0));
        assertTrue(r.found());
        assertEquals(4, r.columns());
        assertEquals(0, r.rise());
        assertEquals(List.of(0, 0, 0, 0), floors(r));
        assertStraight(r);
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
    void aDeckTwoDownSixOutIsReachedAtOnePitch() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(5, 8, -2));
        assertTrue(r.found(), "refused: " + r.failure());
        assertEquals(6, r.columns());
        assertEquals(-2, r.rise());
        assertEquals(-18.43, r.pitchDegrees(), 0.01);
        assertEquals(List.of(-1, -1, -1, -2, -2, -2), floors(r));
        assertStraight(r);
        assertEquals(Landing.STEP_DOWN, r.landing(), "it rests on the lower deck");
        assertEquals(5, r.landI());
        assertEquals(-3, r.landDy());
    }

    @Test
    void aDeckOneUpIsClimbedToItsEdge() {
        // B's deck one higher, its side three columns out: one board over three columns meeting the deck's top edge
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 1));
        assertEquals(3, r.columns());
        assertEquals(1, r.rise());
        assertEquals(List.of(0, 0, 0), floors(r));
        assertStraight(r);
        assertEquals(Landing.RAMP, r.landing());
        assertEquals(3, r.landI());
        assertEquals(0, r.landDy());
    }

    @Test
    void threeUpOverThreeIs45DegreesAndAllowed() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 3));
        assertTrue(r.found(), "refused: " + r.failure());
        assertEquals(45.0, r.pitchDegrees(), 1e-9);
        assertEquals(List.of(0, 1, 2), floors(r));
        assertStraight(r);
    }

    @Test
    void fourUpOverThreeIsTooSteep() {
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 4)).failure(), "a quay four up");
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(1, 6, 2)).failure(),
                "two up right after the first column");
        assertEquals(Failure.TOO_STEEP, PlankRun.find(DEFAULTS, new Grid().deck(0, 0, -2)).failure(),
                "a post two down under the first column and nothing beyond");
    }

    @Test
    void aGentlerMaxPitchRefusesWhatFortyFiveAllows() {
        Grid steep = new Grid().deck(3, 6, 3);
        assertEquals(Failure.TOO_STEEP, PlankRun.find(new Settings(6, 30.0), steep).failure());
        assertTrue(PlankRun.find(new Settings(6, 30.0), new Grid().deck(3, 6, 1)).found(), "18.4 degrees is under 30");
        assertEquals(Failure.TOO_STEEP, PlankRun.find(new Settings(6, 0.0), new Grid().deck(3, 6, 1)).failure(),
                "max_pitch 0 lays level planks only");
        assertEquals(45.0, new Settings(6, 80.0).maxPitch(), "clamped to 45");
    }

    @Test
    void aDeckThreeDownThreeOutIsLaidAt45DegreesOverTheNearerEdge() {
        // the deck under the second column is too steep to land on at once; its third column is 45 degrees away
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(1, 6, -3));
        assertTrue(r.found(), "refused: " + r.failure());
        assertEquals(3, r.columns());
        assertEquals(-3, r.rise());
        assertStraight(r);
    }

    @Test
    void aGangwayBridgesADryGapRatherThanDivingToItsFloor() {
        // from a pier over a dry gap whose floor lies four down, to a pier two up six columns out
        Grid g = new Grid().fill(0, 9, -8, -5, Ground.HOME).fill(6, 9, -4, 1, Ground.HOME);
        Result r = PlankRun.find(DEFAULTS, g);
        assertTrue(r.found(), "refused: " + r.failure());
        assertEquals(6, r.columns());
        assertEquals(2, r.rise());
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void aBlockInTheLevelLineIsClimbedOnto() {
        // a block one up two columns out and a level deck four columns out: the level board would run into the block,
        // so the board climbs onto it
        Grid g = new Grid().deck(4, 7, 0).fill(2, 2, 0, 0, Ground.STURDY);
        Result r = PlankRun.find(DEFAULTS, g);
        assertEquals(2, r.columns());
        assertEquals(1, r.rise());
        assertEquals(Landing.RAMP, r.landing());
    }

    @Test
    void maxLengthIsClampedToTheColumnCount() {
        assertEquals(6, new Settings(9, 45.0).maxLength());
        assertEquals(1, new Settings(0, 45.0).maxLength());
        assertTrue(PlankRun.find(new Settings(9, 0.0), new Grid().deck(5, 9, 0)).found());
        assertFalse(PlankRun.find(new Settings(9, 0.0), new Grid().deck(6, 9, 0)).found());
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
        assertEquals(2, PlankRun.find(DEFAULTS, oneOut).length(), "a one-block gap takes two columns");
    }

    @Test
    void aShipAtThePierEdgeTakesAOneCellPlankFromThePier() {
        assertEquals(1, PlankRun.find(DEFAULTS, new Grid().deck(0, 4, 0)).length());
    }

    // ------------------------------------------------------------------ refusals

    @Test
    void aTakenCellInTheLevelLineIsBlockedThere() {
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(4, 7, 0).fill(1, 1, 0, 0, Ground.SOLID));
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(1, r.blockedAt());
        assertEquals(0, r.blockedDy());
        Result flower = PlankRun.find(DEFAULTS, new Grid().deck(4, 7, 0).take(2, 0));
        assertEquals(Failure.BLOCKED, flower.failure());
        assertEquals(2, flower.blockedAt());
    }

    @Test
    void aTakenCellOnTheSlopeIsBlockedAtItsHeight() {
        // two up over four: the third column's cell is one block up
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(4, 7, 2).fill(2, 2, 1, 1, Ground.SOLID));
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(2, r.blockedAt());
        assertEquals(1, r.blockedDy());
    }

    @Test
    void theBoardsTopNeedsRoomWhereItLeavesItsCell() {
        // one up over three: the last column's board top reaches 2 px into the cell above; a beam there blocks it
        Result r = PlankRun.find(DEFAULTS, new Grid().deck(3, 6, 1).fill(2, 2, 1, 1, Ground.SOLID));
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(2, r.blockedAt());
        assertEquals(1, r.blockedDy());
    }

    @Test
    void aDeckPastTheReachIsTooFar() {
        assertEquals(Failure.TOO_FAR, PlankRun.find(DEFAULTS, new Grid().deck(7, 9, 0)).failure());
        assertEquals(Failure.TOO_FAR, PlankRun.find(DEFAULTS, new Grid().deck(8, 9, 1)).failure(), "one up, past the reach");
        assertEquals(Failure.TOO_FAR, PlankRun.find(new Settings(2, 45.0), new Grid().deck(3, 9, 0)).failure());
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
        Result r = PlankRun.find(DEFAULTS, new Grid().fill(2, 2, -1, 0, Ground.SOLID));
        assertEquals(Failure.BLOCKED, r.failure());
        assertEquals(2, r.blockedAt());
    }

    // ------------------------------------------------------------------ the walking surface

    /**
     * Every board up to 45 degrees, rising and falling, over every length: the steps are inside their cell, 4 px long,
     * monotone along the run, never more than half a block (in fact a quarter) above or below the last one, and the
     * first step meets the deck the run starts from and the last one the landing within half a block.
     */
    @Test
    void theStepsOfEveryStraightBoardWalkWithoutAJump() {
        for (int columns = 1; columns <= PlankRun.MAX_LENGTH; columns++) {
            for (int rise = -columns; rise <= columns; rise++) {
                List<Cell> cells = PlankRun.cells(columns, rise);
                double[][] tops = new double[columns][PlankRun.STEPS_PER_CELL];
                for (double[] row : tops) java.util.Arrays.fill(row, Double.NEGATIVE_INFINITY);
                for (Cell c : cells) {
                    for (double[] step : PlankRun.steps(c.near() * 16, c.far() * 16, 2)) {
                        assertTrue(step[2] >= 0 && step[3] <= 16 && step[2] < step[3], "a step leaves its cell");
                        assertEquals(4.0, step[1] - step[0], EPS, "a step is 4 px long");
                        int k = (int) Math.round(step[0] / 4.0);
                        tops[c.i()][k] = Math.max(tops[c.i()][k], c.dy() * 16 + step[3]);
                    }
                }
                String what = rise + " over " + columns;
                double last = 0.0; // the deck the run starts from
                for (int i = 0; i < columns; i++) {
                    for (int k = 0; k < PlankRun.STEPS_PER_CELL; k++) {
                        double top = tops[i][k];
                        assertTrue(top > Double.NEGATIVE_INFINITY, what + ": no step at " + i + "/" + k);
                        assertTrue(Math.abs(top - last) <= 8.0 + EPS, what + ": a step of " + (top - last) + " px");
                        if (rise > 0) assertTrue(top >= last - EPS || i + k == 0, what + ": not monotone up");
                        if (rise < 0 && i + k > 0) assertTrue(top <= last + EPS, what + ": not monotone down");
                        if (rise == 0) assertEquals(2.0, top, EPS, what + ": a level board is 2 px");
                        assertTrue(Math.abs(top - last) <= 4.0 + 2.0 + EPS, what + ": more than a quarter block");
                        last = top;
                    }
                }
                assertTrue(Math.abs(last - rise * 16.0) <= 8.0 + EPS, what + ": the last step is " + last + " px against "
                        + rise * 16 + " at the landing");
            }
        }
    }

    // ------------------------------------------------------------------ break

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

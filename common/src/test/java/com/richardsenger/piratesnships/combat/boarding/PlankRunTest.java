package com.richardsenger.piratesnships.combat.boarding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlankRunTest {

    /** A probe over a row of cells: {@code blocked} indices are not free, {@code landings} land. Records what was asked. */
    private static final class Row implements PlankRun.Probe {
        final List<Integer> blocked;
        final Map<Integer, PlankRun.Landing> landings;
        final List<Integer> asked = new ArrayList<>();

        Row(List<Integer> blocked, Map<Integer, PlankRun.Landing> landings) {
            this.blocked = blocked;
            this.landings = landings;
        }

        @Override
        public boolean free(int i) {
            asked.add(i);
            return !blocked.contains(i);
        }

        @Override
        public @Nullable PlankRun.Landing landing(int i) {
            return landings.get(i);
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

    // ------------------------------------------------------------------ compute

    @Test
    void theRunEndsAtTheFirstCellThatLands() {
        Row row = new Row(List.of(), Map.of(2, PlankRun.Landing.LEVEL, 3, PlankRun.Landing.LEVEL));
        PlankRun.Result r = PlankRun.compute(4, row);
        assertTrue(r.found());
        assertEquals(3, r.length());
        assertEquals(2, r.tip());
        assertEquals(PlankRun.Landing.LEVEL, r.landing());
        assertEquals(List.of(0, 1, 2), row.asked, "cells past the landing are not looked at");
    }

    @Test
    void aLandingBeyondMaxLengthIsOutOfReach() {
        PlankRun.Result r = PlankRun.compute(4, new Row(List.of(), Map.of(4, PlankRun.Landing.LEVEL)));
        assertFalse(r.found());
        assertEquals(PlankRun.Failure.NO_DECK, r.failure());
        assertTrue(PlankRun.compute(2, new Row(List.of(), Map.of(2, PlankRun.Landing.LEVEL))).failure() == PlankRun.Failure.NO_DECK,
                "max_length 2 reaches cells 0 and 1 only");
    }

    @Test
    void maxLengthIsClampedToTheSegmentCount() {
        assertTrue(PlankRun.compute(9, new Row(List.of(), Map.of(3, PlankRun.Landing.STEP_UP))).found());
        assertFalse(PlankRun.compute(9, new Row(List.of(), Map.of(4, PlankRun.Landing.LEVEL))).found());
        assertTrue(PlankRun.compute(0, new Row(List.of(), Map.of(0, PlankRun.Landing.LEVEL))).found(), "at least one cell");
    }

    @Test
    void aBlockedCellBeforeTheLandingStopsTheRun() {
        PlankRun.Result r = PlankRun.compute(4, new Row(List.of(1), Map.of(2, PlankRun.Landing.LEVEL)));
        assertEquals(PlankRun.Failure.NO_DECK, r.failure());
        assertEquals(PlankRun.Failure.BLOCKED, PlankRun.compute(4, new Row(List.of(0), Map.of(0, PlankRun.Landing.LEVEL))).failure(),
                "a blocked first cell is reported as blocked");
    }

    @Test
    void aBlockedCellAfterTheLandingDoesNotMatter() {
        assertEquals(2, PlankRun.compute(4, new Row(List.of(2, 3), Map.of(1, PlankRun.Landing.STEP_DOWN))).length());
    }

    // ------------------------------------------------------------------ classify

    @Test
    void aSturdyBlockUnderTheCellIsALevelLanding() {
        assertEquals(PlankRun.Landing.LEVEL, PlankRun.classify(true, true, false, false, false));
        assertEquals(PlankRun.Landing.LEVEL, PlankRun.classify(true, true, true, true, false), "level wins over the others");
    }

    @Test
    void aDeckOneBlockLowerIsAStepDownOnlyWithNothingInBetween() {
        assertEquals(PlankRun.Landing.STEP_DOWN, PlankRun.classify(false, false, true, false, false));
        assertNull(PlankRun.classify(false, true, true, false, false), "a fence or a slab under the cell is in the way");
    }

    @Test
    void aDeckOneBlockHigherAheadIsAStepUpOnlyWithRoomAboveIt() {
        assertEquals(PlankRun.Landing.STEP_UP, PlankRun.classify(false, false, false, true, false));
        assertNull(PlankRun.classify(false, false, false, true, true), "a rail on the higher deck blocks the step");
    }

    @Test
    void openWaterIsNoLanding() {
        assertNull(PlankRun.classify(false, false, false, false, false));
    }

    // ------------------------------------------------------------------ tip and break

    @Test
    void onlyTheLastSegmentIsTheTip() {
        assertTrue(PlankRun.isTip(3, 4));
        assertFalse(PlankRun.isTip(2, 4));
        assertTrue(PlankRun.isTip(0, 1));
    }

    @Test
    void theRunBreaksWhenTheFarEndDriftsTooFarOrTheShipIsGone() {
        assertFalse(PlankRun.breaks(true, 0.0, 1.5));
        assertFalse(PlankRun.breaks(true, 1.5, 1.5), "exactly at the limit holds");
        assertTrue(PlankRun.breaks(true, 1.51, 1.5));
        assertTrue(PlankRun.breaks(false, 0.0, 1.5), "the far ship is gone");
        assertTrue(PlankRun.breaks(true, Double.NaN, 1.5), "an unknown distance breaks");
    }
}

package com.richardsenger.piratesnships.crew.galley;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

/** The crew's meal schedule (CRW2). */
class MealRulesTest {

    private static final List<Long> DEFAULT = MealRules.parseTimes(MealRules.DEFAULT_TIMES);

    @Test
    void defaultsAreNoonAndSunsetBeforeNightfall() {
        assertEquals(List.of(6000L, 12000L), DEFAULT);
        assertTrue(DEFAULT.stream().allMatch(t -> t < 12542), "every default meal is before the crew turns in");
    }

    @Test
    void parsingSortsWrapsAndIgnoresJunk() {
        assertEquals(List.of(0L, 500L, 6000L), MealRules.parseTimes(List.of("6000", " 24500 ", "x", "", "-24000", "6000")));
        assertEquals(List.of(), MealRules.parseTimes(List.of()));
    }

    @Test
    void aRunningClockServesTheMealItPasses() {
        assertTrue(MealRules.mealStarts(5999, 6000, DEFAULT));
        assertFalse(MealRules.mealStarts(6000, 6001, DEFAULT), "only once");
        assertFalse(MealRules.mealStarts(5998, 5999, DEFAULT));
        assertTrue(MealRules.mealStarts(24000L * 7 + 11990, 24000L * 7 + 12001, DEFAULT), "any day");
        assertTrue(MealRules.mealStarts(23990, 24010, MealRules.parseTimes(List.of("0"))), "midnight across the day line");
    }

    @Test
    void jumpsAndBackwardsSkipTheMeal() {
        assertFalse(MealRules.mealStarts(1000, 18000, DEFAULT), "/time set over noon and sunset");
        assertFalse(MealRules.mealStarts(6001, 6000, DEFAULT), "backwards");
        assertFalse(MealRules.mealStarts(6000, 6000, DEFAULT), "a stopped clock");
        assertTrue(MealRules.mealStarts(6000 - MealRules.MAX_STEP, 6000, DEFAULT), "the largest step still serves");
        assertFalse(MealRules.mealStarts(5999 - MealRules.MAX_STEP, 6000, DEFAULT), "one more is a jump");
        assertFalse(MealRules.mealStarts(5999, 6000, List.of()), "no meal times");
    }

    @Test
    void mealEndsAfterMealTicks() {
        assertEquals(1100, MealRules.mealEnd(1000, 100));
        assertEquals(1001, MealRules.mealEnd(1000, 0), "at least one tick");
    }

    @Test
    void spotsAreTheEightNeighboursSidesFirst() {
        BlockPos p = new BlockPos(10, 5, 10);
        List<BlockPos> spots = MealRules.spotsAround(p);
        assertEquals(8, spots.size());
        assertEquals(8, new HashSet<>(spots).size());
        for (int i = 0; i < 8; i++) {
            BlockPos s = spots.get(i);
            assertEquals(5, s.getY());
            assertEquals(i < 4 ? 1 : 2, s.distManhattan(p), "sides first, then corners: " + s);
        }
    }

    @Test
    void dinerFacesTheBlock() {
        BlockPos block = new BlockPos(0, 0, 0);
        assertEquals(180f, Math.abs(MealRules.facingYaw(new BlockPos(0, 0, 1), block, null)), 1e-4, "south of it: faces north");
        assertEquals(0f, MealRules.facingYaw(new BlockPos(0, 0, -1), block, null), 1e-4, "north of it: faces south");
        assertEquals(90f, MealRules.facingYaw(new BlockPos(1, 0, 0), block, null), 1e-4, "east of it: faces west");
        assertEquals(-90f, MealRules.facingYaw(new BlockPos(-1, 0, 0), block, null), 1e-4, "west of it: faces east");
        // a ship turned 90 degrees about y (plot +x points to world -z): east of it in the plot faces world south
        Quaterniond turned = new Quaterniond().rotateY(Math.toRadians(90));
        assertEquals(0f, MealRules.facingYaw(new BlockPos(1, 0, 0), block, turned), 1e-3);
    }
}

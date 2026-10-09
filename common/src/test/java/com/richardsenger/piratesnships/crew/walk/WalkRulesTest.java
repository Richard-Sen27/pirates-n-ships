package com.richardsenger.piratesnships.crew.walk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.crew.walk.WalkRules.Step;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WalkRulesTest {

    @Test
    void arrivesWithinTheDistance() {
        assertEquals(Step.ARRIVED, WalkRules.step(1.5, 1.5, 3, 200));
        assertEquals(Step.ARRIVED, WalkRules.step(0.2, 1.5, 0, 200));
        assertEquals(Step.WALK, WalkRules.step(1.51, 1.5, 3, 200));
    }

    @Test
    void timesOutAtTheTimeoutNotBefore() {
        assertEquals(Step.WALK, WalkRules.step(6, 1.5, 199, 200));
        assertEquals(Step.TIMED_OUT, WalkRules.step(6, 1.5, 200, 200));
        assertEquals(Step.TIMED_OUT, WalkRules.step(6, 1.5, 5000, 200));
    }

    @Test
    void arrivalWinsOverTheTimeout() {
        assertEquals(Step.ARRIVED, WalkRules.step(1.0, 1.5, 300, 200));
    }

    @Test
    void repathsWithoutAPath() {
        assertTrue(WalkRules.repath(false, false, 7, 20));
        assertTrue(WalkRules.repath(false, true, 0, 20));
    }

    @Test
    void repathsEveryIntervalOnlyWhileTheShipMoves() {
        assertFalse(WalkRules.repath(true, false, 20, 20));
        assertFalse(WalkRules.repath(true, true, 0, 20));
        assertFalse(WalkRules.repath(true, true, 19, 20));
        assertTrue(WalkRules.repath(true, true, 20, 20));
        assertTrue(WalkRules.repath(true, true, 40, 20));
        assertFalse(WalkRules.repath(true, true, 41, 20));
    }

    @Test
    void movingThresholds() {
        assertFalse(WalkRules.moving(0.0, 0.0));
        assertFalse(WalkRules.moving(0.05, 0.02));
        assertTrue(WalkRules.moving(2.0, 0.0));
        assertTrue(WalkRules.moving(0.0, 0.1));
    }

    @Test
    void targetRoundTripsThroughItsCodec() {
        WalkTarget t = new WalkTarget(WalkTarget.Purpose.HAMMOCK, new UUID(3, 4), new BlockPos(1, 2, 3), new BlockPos(1, 2, 4), 1234L);
        var json = WalkTarget.CODEC.encodeStart(JsonOps.INSTANCE, t).getOrThrow();
        assertEquals(t, WalkTarget.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(t.goesTo(WalkTarget.Purpose.HAMMOCK, new UUID(3, 4), new BlockPos(1, 2, 4)));
        assertFalse(t.goesTo(WalkTarget.Purpose.MEAL, new UUID(3, 4), new BlockPos(1, 2, 4)));
        assertFalse(t.goesTo(WalkTarget.Purpose.HAMMOCK, new UUID(3, 4), new BlockPos(1, 2, 3)));
    }
}

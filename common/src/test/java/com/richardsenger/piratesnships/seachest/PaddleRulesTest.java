package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.survival.cold.ColdWaterRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaddleRulesTest {

    private static final PaddleRules.Params P = new PaddleRules.Params(1.5, 0.5, 60.0, 1.5);
    private static final FloatRules.Params WATER = FloatRules.Params.of(0.45);
    private static final PaddleRules.Input FORWARD = PaddleRules.Input.of(0f, 1f);

    /** Runs {@code ticks} ticks of the chest's horizontal motion (drag then stroke, as in its tick) from rest. */
    private static double[] run(PaddleRules.Input in, float yaw, int ticks) {
        double vx = 0, vz = 0, x = 0, z = 0;
        for (int i = 0; i < ticks; i++) {
            PaddleRules.Stroke s = PaddleRules.stroke(in, true, yaw, P, WATER);
            yaw += s.yawDelta();
            vx = (vx + s.accelX()) * WATER.waterRetention();
            vz = (vz + s.accelZ()) * WATER.waterRetention();
            x += vx;
            z += vz;
        }
        return new double[]{x, z, vx, vz, yaw};
    }

    @Test
    void decodesVanillaPassengerInput() {
        assertEquals(new PaddleRules.Input(true, false, false, false), PaddleRules.Input.of(0f, 1f));
        assertEquals(new PaddleRules.Input(false, true, false, false), PaddleRules.Input.of(0f, -1f));
        assertEquals(new PaddleRules.Input(false, false, true, false), PaddleRules.Input.of(1f, 0f));
        assertEquals(new PaddleRules.Input(false, false, false, true), PaddleRules.Input.of(-1f, 0f));
        assertEquals(PaddleRules.Input.NONE, PaddleRules.Input.of(0.05f, -0.05f));
        // A key decayed by vanilla's 0.98 per tick for a few ticks still counts
        assertTrue(PaddleRules.Input.of(0f, (float) Math.pow(0.98, 10)).forward());
        assertEquals(0, new PaddleRules.Input(true, true, true, true).thrust());
        assertEquals(0, new PaddleRules.Input(true, true, true, true).turn());
    }

    @Test
    void forwardSettlesAtThePaddleSpeed() {
        double[] r = run(FORWARD, 0f, 400);
        assertEquals(1.5 / 20.0, r[3], 1e-6, "steady speed along +Z at yaw 0");
        assertEquals(0.0, r[2], 1e-9);
    }

    @Test
    void threeSecondsCoverMoreThanTwoBlocks() {
        double z = run(FORWARD, 0f, 60)[1];
        assertTrue(z > 2.0 && z < 4.5, "60 ticks from rest: " + z);
    }

    @Test
    void headingFollowsVanillaYaw() {
        // yaw 90 faces -X (west), yaw 180 faces -Z
        double[] west = run(FORWARD, 90f, 400);
        assertEquals(-1.5 / 20.0, west[2], 1e-6);
        assertEquals(0.0, west[3], 1e-6);
        double[] north = run(FORWARD, 180f, 400);
        assertEquals(-1.5 / 20.0, north[3], 1e-6);
    }

    @Test
    void backingIsSlower() {
        double[] r = run(PaddleRules.Input.of(0f, -1f), 0f, 400);
        assertEquals(-0.75 / 20.0, r[3], 1e-6);
    }

    @Test
    void leftTurnsYawDownAtTheTurnRate() {
        PaddleRules.Stroke left = PaddleRules.stroke(PaddleRules.Input.of(1f, 0f), true, 0f, P, WATER);
        PaddleRules.Stroke right = PaddleRules.stroke(PaddleRules.Input.of(-1f, 0f), true, 0f, P, WATER);
        assertEquals(-3.0f, left.yawDelta(), 1e-6);
        assertEquals(3.0f, right.yawDelta(), 1e-6);
        assertEquals(0.0, left.accelX(), 1e-12, "turning on the spot makes no headway");
        assertEquals(0.0, left.accelZ(), 1e-12);
        assertTrue(left.paddling(), "turning is a stroke");
        assertEquals(-60.0, run(PaddleRules.Input.of(1f, 0f), 0f, 20)[4], 1e-3, "one second of left");
    }

    @Test
    void withoutAPaddleNothingHappens() {
        assertSame(PaddleRules.Stroke.NONE, PaddleRules.stroke(FORWARD, false, 0f, P, WATER));
        assertSame(PaddleRules.Stroke.NONE, PaddleRules.stroke(PaddleRules.Input.NONE, true, 0f, P, WATER));
        assertEquals(0.0f, PaddleRules.exhaustion(PaddleRules.Stroke.NONE, P));
    }

    @Test
    void strokesCostSwimmingHunger() {
        PaddleRules.Stroke s = PaddleRules.stroke(FORWARD, true, 0f, P, WATER);
        // 0.01 per block × 0.075 blocks per tick × 1.5
        assertEquals(0.001125f, PaddleRules.exhaustion(s, P), 1e-7);
        assertEquals(0.0f, PaddleRules.exhaustion(s, new PaddleRules.Params(1.5, 0.5, 60.0, 0.0)));
    }

    @Test
    void theRiderFreezesInColdWaterThoughRiding() {
        assertEquals(ColdWaterRules.Outcome.FREEZES,
                ColdWaterRules.judge(true, PaddleRules.riderInColdWater(true, true, true, false, true, false)));
        assertEquals(ColdWaterRules.Outcome.WARM,
                ColdWaterRules.judge(true, PaddleRules.riderInColdWater(true, true, true, false, true, true)));
        assertEquals(ColdWaterRules.Outcome.CREATIVE,
                ColdWaterRules.judge(true, PaddleRules.riderInColdWater(true, true, true, true, true, false)));
        assertEquals(ColdWaterRules.Outcome.NOT_IN_COLD_WATER,
                ColdWaterRules.judge(true, PaddleRules.riderInColdWater(true, true, false, false, true, false)));
        assertFalse(ColdWaterRules.judge(true, PaddleRules.riderInColdWater(true, true, true, false, false, false))
                == ColdWaterRules.Outcome.FREEZES, "leather armour protects");
    }
}

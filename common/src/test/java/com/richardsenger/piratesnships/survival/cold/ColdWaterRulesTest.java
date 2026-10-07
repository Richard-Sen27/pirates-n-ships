package com.richardsenger.piratesnships.survival.cold;

import com.richardsenger.piratesnships.survival.cold.ColdWaterRules.Outcome;
import com.richardsenger.piratesnships.survival.cold.ColdWaterRules.Subject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Cold water: who freezes, and how the meter moves against vanilla's thaw. */
class ColdWaterRulesTest {

    private static final int REQUIRED = 140; // Entity.BASE_TICKS_REQUIRED_TO_FREEZE

    /** A survival player wading in cold water: freezes. */
    private static Subject wader() {
        return new Subject(true, true, true, false, true, false, false, false, () -> false);
    }

    private static Subject with(Subject s, String what) {
        return switch (what) {
            case "dead" -> new Subject(false, s.inWater(), s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), s.riding(), s.warm(), s.onShip());
            case "dry" -> new Subject(s.alive(), false, s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), s.riding(), s.warm(), s.onShip());
            case "warmBiome" -> new Subject(s.alive(), s.inWater(), false, s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), s.riding(), s.warm(), s.onShip());
            case "creative" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), true, s.canFreeze(), s.aquatic(), s.riding(), s.warm(), s.onShip());
            case "leather" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), s.creativeOrSpectator(), false, s.aquatic(), s.riding(), s.warm(), s.onShip());
            case "fish" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), true, s.riding(), s.warm(), s.onShip());
            case "boat" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), true, s.warm(), s.onShip());
            case "warm" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), s.riding(), true, s.onShip());
            case "ship" -> new Subject(s.alive(), s.inWater(), s.coldBiome(), s.creativeOrSpectator(), s.canFreeze(), s.aquatic(), s.riding(), s.warm(), () -> true);
            default -> throw new IllegalArgumentException(what);
        };
    }

    @Test
    void waderInColdWaterFreezes() {
        assertEquals(Outcome.FREEZES, ColdWaterRules.judge(true, wader()));
    }

    @Test
    void disabledNeverFreezes() {
        assertEquals(Outcome.DISABLED, ColdWaterRules.judge(false, wader()));
    }

    @Test
    void needsColdWater() {
        assertEquals(Outcome.NOT_IN_COLD_WATER, ColdWaterRules.judge(true, with(wader(), "dry")));
        assertEquals(Outcome.NOT_IN_COLD_WATER, ColdWaterRules.judge(true, with(wader(), "warmBiome")));
    }

    @Test
    void exemptions() {
        assertEquals(Outcome.DEAD, ColdWaterRules.judge(true, with(wader(), "dead")));
        assertEquals(Outcome.CREATIVE, ColdWaterRules.judge(true, with(wader(), "creative")));
        assertEquals(Outcome.CANNOT_FREEZE, ColdWaterRules.judge(true, with(wader(), "leather")));
        assertEquals(Outcome.AQUATIC, ColdWaterRules.judge(true, with(wader(), "fish")));
        assertEquals(Outcome.RIDING, ColdWaterRules.judge(true, with(wader(), "boat")));
        assertEquals(Outcome.WARM, ColdWaterRules.judge(true, with(wader(), "warm")));
        assertEquals(Outcome.ON_SHIP, ColdWaterRules.judge(true, with(wader(), "ship")));
    }

    @Test
    void shipIsAskedOnlyWhenEverythingElseFreezes() {
        AtomicInteger asked = new AtomicInteger();
        Subject boat = new Subject(true, true, true, false, true, false, true, false, () -> { asked.incrementAndGet(); return false; });
        Subject dry = new Subject(true, false, true, false, true, false, false, false, () -> { asked.incrementAndGet(); return false; });
        ColdWaterRules.judge(true, boat);
        ColdWaterRules.judge(true, dry);
        ColdWaterRules.judge(false, wader());
        assertEquals(0, asked.get());
        Subject swimmer = new Subject(true, true, true, false, true, false, false, false, () -> { asked.incrementAndGet(); return false; });
        assertEquals(Outcome.FREEZES, ColdWaterRules.judge(true, swimmer));
        assertEquals(1, asked.get());
    }

    @Test
    void incrementGivesBackVanillasThawPlusTheGain() {
        // vanilla thawed 52 -> 50 this tick; we put back the 2 and add 1
        assertEquals(53, ColdWaterRules.nextTicksFrozen(50, REQUIRED, 1));
        assertEquals(55, ColdWaterRules.nextTicksFrozen(50, REQUIRED, 3));
        assertEquals(3, ColdWaterRules.nextTicksFrozen(0, REQUIRED, 1));
    }

    @Test
    void capKeepsVanillasDamageCheckFull() {
        int capped = ColdWaterRules.nextTicksFrozen(140, REQUIRED, 1);
        assertEquals(REQUIRED + ColdWaterRules.VANILLA_THAW_PER_TICK, capped);
        // after vanilla's next thaw the meter still reads full (LivingEntity.aiStep's isFullyFrozen check)
        assertEquals(REQUIRED, ColdWaterRules.afterVanillaThaw(capped));
        assertEquals(capped, ColdWaterRules.nextTicksFrozen(ColdWaterRules.afterVanillaThaw(capped), REQUIRED, 1));
    }

    @Test
    void neverLowersAHigherMeter() {
        assertEquals(500, ColdWaterRules.nextTicksFrozen(500, REQUIRED, 1));
    }

    @Test
    void steadyStateRisesByTheGainPerTick() {
        int meter = 0;
        int seen = 0;
        for (int tick = 0; tick < 60; tick++) {
            int vanillaView = ColdWaterRules.afterVanillaThaw(meter);
            if (tick > 1) assertEquals(seen + 1, vanillaView, "tick " + tick);
            seen = vanillaView;
            meter = ColdWaterRules.nextTicksFrozen(vanillaView, REQUIRED, 1);
        }
    }

    @Test
    void fullAfterAsLongAsInPowderSnow() {
        assertEquals(REQUIRED, ColdWaterRules.ticksUntilFull(REQUIRED, 1));
        assertEquals(70, ColdWaterRules.ticksUntilFull(REQUIRED, 2));
        assertTrue(ColdWaterRules.ticksUntilFull(REQUIRED, 20) <= 8);
    }

    @Test
    void thawOutsideIsVanillas() {
        // outside cold water we do nothing: the meter drops by vanilla's 2 per tick
        assertEquals(2, ColdWaterRules.VANILLA_THAW_PER_TICK);
        assertEquals(138, ColdWaterRules.afterVanillaThaw(140));
        assertEquals(0, ColdWaterRules.afterVanillaThaw(1));
    }
}

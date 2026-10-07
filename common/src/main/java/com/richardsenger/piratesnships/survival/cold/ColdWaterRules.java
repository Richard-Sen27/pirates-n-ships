package com.richardsenger.piratesnships.survival.cold;

import java.util.function.BooleanSupplier;

/**
 * Cold water (docs/design.md §14), pure. Being in the water of a cold biome fills vanilla's freezing meter
 * ({@code Entity.ticksFrozen}) the way powder snow does, so vanilla's frost overlay, slowdown and freezing damage follow
 * without new code.
 *
 * <h2>Working with vanilla's meter</h2>
 * Every tick {@code LivingEntity.aiStep} adds 1 in powder snow (capped at {@code getTicksRequiredToFreeze()}, 140) and
 * otherwise thaws 2, then deals freezing damage every 40 ticks while the meter is at or above that cap. Our hook runs
 * after the entity's tick, so vanilla has just thawed 2: {@link #nextTicksFrozen} gives those back plus the increment,
 * and caps at the requirement <em>plus</em> the thaw, so that after vanilla's next thaw the meter still reads full and
 * the damage check passes. Out of cold water (or when exempt) we do nothing and vanilla thaws at its own rate.
 */
public final class ColdWaterRules {

    /** What vanilla takes off the meter every tick outside powder snow ({@code LivingEntity.aiStep}). */
    public static final int VANILLA_THAW_PER_TICK = 2;

    /** Why an entity does or doesn't freeze this tick. */
    public enum Outcome {
        FREEZES,
        /** {@code survival.cold_water.enabled} is off. */
        DISABLED,
        /** Dead or dying: vanilla doesn't touch the meter either. */
        DEAD,
        /** Not in water, or the water's biome is not in {@code #pirates_n_ships:cold_water}. */
        NOT_IN_COLD_WATER,
        /** Creative or spectator player. */
        CREATIVE,
        /** Vanilla's {@code canFreeze()} is false: leather armour, {@code #freeze_immune_entity_types}, spectators. */
        CANNOT_FREEZE,
        /** A water creature (fish, squid, sharks, drowned): lives in that water. */
        AQUATIC,
        /** Riding anything (a boat). */
        RIDING,
        /** Has the warm effect. */
        WARM,
        /** Standing on or tracked by a ship (dry hull). */
        ON_SHIP
    }

    /**
     * What the rule needs to know about an entity. {@code onShip} is asked last and only when every other check let
     * the entity freeze, because it asks Sable.
     */
    public record Subject(boolean alive, boolean inWater, boolean coldBiome, boolean creativeOrSpectator,
                          boolean canFreeze, boolean aquatic, boolean riding, boolean warm, BooleanSupplier onShip) {
    }

    private ColdWaterRules() {
    }

    /** Whether {@code s} freezes this tick, and if not, the first reason why not. */
    public static Outcome judge(boolean enabled, Subject s) {
        if (!enabled) return Outcome.DISABLED;
        if (!s.alive()) return Outcome.DEAD;
        if (!s.inWater() || !s.coldBiome()) return Outcome.NOT_IN_COLD_WATER;
        if (s.creativeOrSpectator()) return Outcome.CREATIVE;
        if (!s.canFreeze()) return Outcome.CANNOT_FREEZE;
        if (s.aquatic()) return Outcome.AQUATIC;
        if (s.riding()) return Outcome.RIDING;
        if (s.warm()) return Outcome.WARM;
        if (s.onShip().getAsBoolean()) return Outcome.ON_SHIP;
        return Outcome.FREEZES;
    }

    /**
     * The meter after our hook for an entity that freezes, given the meter {@code current} right after the entity's
     * tick (vanilla has thawed it by {@link #VANILLA_THAW_PER_TICK}), the entity's {@code required} ticks to freeze
     * and the configured gain {@code perTick}. Never lowers the meter (e.g. one raised by something else).
     */
    public static int nextTicksFrozen(int current, int required, int perTick) {
        int cap = required + VANILLA_THAW_PER_TICK;
        int next = Math.min(cap, current + VANILLA_THAW_PER_TICK + Math.max(0, perTick));
        return Math.max(current, next);
    }

    /** The meter as vanilla sees it in its damage check one tick later (after its thaw). */
    public static int afterVanillaThaw(int ticksFrozen) {
        return Math.max(0, ticksFrozen - VANILLA_THAW_PER_TICK);
    }

    /**
     * Ticks in cold water from an empty meter until vanilla first sees it full ({@code required}), for the playtest
     * notes and tests: with a gain of 1 exactly {@code required} ticks, as in powder snow (140).
     */
    public static int ticksUntilFull(int required, int perTick) {
        int meter = 0;
        int ticks = 0;
        while (afterVanillaThaw(meter) < required) {
            meter = nextTicksFrozen(afterVanillaThaw(meter), required, perTick);
            ticks++;
            if (ticks > 1_000_000) throw new IllegalStateException("never freezes");
        }
        return ticks;
    }
}

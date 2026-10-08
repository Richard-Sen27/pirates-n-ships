package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.HullAnalysis;
import com.richardsenger.piratesnships.ship.hull.HullGrid;
import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodSimulation;

/**
 * Breath below the flood water inside a hull (FLD1b, docs/design.md §4.4, §4.5): the pure rules, no world access.
 * {@link FloodBreathing} applies them to the living entities aboard a ship each hull tick.
 *
 * <p>Vanilla drains air only while the eye is in the world's water. Inside a flooding hull that misses two cases: the
 * eye is below the flood level in a cell the server still counts dry (a cell floods, and leaves Sable's dry region, only
 * once the water passes its centre), and flood water standing higher than the sea, where there is no world water at all.
 * Here the eye is under water when it lies in a compartment cell and below that compartment's level, both measured
 * along the analysis up vector like the simulation's own heights. Where vanilla already sees water at the eye this rule
 * does nothing, so air never drains twice.
 *
 * <p>Air follows vanilla's {@code LivingEntity#baseTick}: one point per tick (Respiration's oxygen bonus may skip a
 * tick, {@link #consumes}), and at -20 the air resets to 0 and the entity takes 2 drowning damage. Because vanilla
 * refills 4 air every tick it thinks the eye is dry, the air is carried on from the value this rule set last tick
 * ({@link #step}), not from what the refill left.
 */
public final class FloodBreath {

    /** Less water than this (blocks) in a compartment drowns nobody (the same film {@link FloodSurfaces} hides). */
    static final double MIN_VOLUME = FloodSurfaces.MIN_VOLUME;
    /** Vanilla's drowning step: at this air the entity takes {@link #DROWN_DAMAGE} and its air resets to 0. */
    public static final int DROWN_AT = -20;
    public static final float DROWN_DAMAGE = 2.0f;
    /** How long vanilla's turtle helmet keeps its wearer breathing once the head is under (its effect's duration). */
    public static final int TURTLE_HELMET_TICKS = 200;
    /** {@link #step}'s "no air set last tick". */
    public static final int NO_LAST = Integer.MIN_VALUE;

    private FloodBreath() {
    }

    /**
     * The compartment whose flood water covers plot point {@code (x, y, z)} (an entity's eye), or -1 when the point is
     * not in a compartment cell, the compartment holds less than {@link #MIN_VOLUME}, or the point is at or above the
     * water level.
     */
    public static int floodedCompartmentAt(FloodSimulation sim, double x, double y, double z) {
        HullAnalysis a = sim.analysis();
        HullGrid g = a.grid();
        int c = a.compartmentAt((int) Math.floor(x) - g.originX(), (int) Math.floor(y) - g.originY(),
                (int) Math.floor(z) - g.originZ());
        if (c < 0 || sim.volume(c) < MIN_VOLUME) {
            return -1;
        }
        return isBelow(a.up(), x, y, z, sim.level(c)) ? c : -1;
    }

    /** Whether plot point {@code (x, y, z)} lies strictly below a ship-frame water level along {@code up}. */
    static boolean isBelow(HullVec up, double x, double y, double z, double level) {
        return up.x() * x + up.y() * y + up.z() * z < level;
    }

    /**
     * Whether an entity keeps its breath: like vanilla's {@code LivingEntity#baseTick}, when its type breathes under
     * water ({@code #minecraft:can_breathe_under_water}), it has Water Breathing or Conduit Power, or it is a player whose
     * abilities make it invulnerable (creative, spectator).
     *
     * <p>Turtle helmet: vanilla gives its wearer a hidden 10-second Water Breathing every tick its eye is out of the
     * world's water, so that it runs out 10 seconds after diving. Under flood water vanilla never sees the eye under, so
     * the helmet would renew it forever. {@code helmetBreathingOnly} says the only Water Breathing is the helmet's; it
     * then counts for {@link #TURTLE_HELMET_TICKS} ticks under the flood water, as in the sea.
     */
    public static boolean canBreathe(boolean breathesUnderWater, boolean waterBreathing, boolean helmetBreathingOnly,
                                     int ticksUnder, boolean invulnerablePlayer) {
        if (breathesUnderWater || invulnerablePlayer) {
            return true;
        }
        if (!waterBreathing) {
            return false;
        }
        return !helmetBreathingOnly || ticksUnder < TURTLE_HELMET_TICKS;
    }

    /** Whether this rule drains: the eye is under the flood water, vanilla does not see water there, and it cannot breathe. */
    public static boolean drains(boolean eyeUnderFlood, boolean vanillaEyeInWater, boolean canBreathe) {
        return eyeUnderFlood && !vanillaEyeInWater && !canBreathe;
    }

    /**
     * Vanilla's {@code LivingEntity#decreaseAirSupply} roll (protected there, so copied): with an oxygen bonus (the
     * Respiration enchantment) a tick costs no air when {@code roll >= 1 / (bonus + 1)}.
     *
     * @param roll a uniform random number in [0, 1)
     */
    public static boolean consumes(double oxygenBonus, double roll) {
        return !(oxygenBonus > 0 && roll >= 1.0 / (oxygenBonus + 1.0));
    }

    /** One tick of air under the flood water: the new air and whether the entity takes drowning damage now. */
    public record Step(int air, boolean drown) {
    }

    /**
     * The air after one tick under flood water. {@code lastSet} is the air this rule set last tick ({@link #NO_LAST} on
     * the first tick under): vanilla's refill since then is undone, anything else that took air (lower than lastSet) is
     * kept.
     */
    public static Step step(int air, int lastSet, boolean consume) {
        int from = lastSet == NO_LAST ? air : Math.min(air, lastSet);
        int next = consume ? from - 1 : from;
        return next <= DROWN_AT ? new Step(0, true) : new Step(next, false);
    }
}

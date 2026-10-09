package com.richardsenger.piratesnships.hazards.waves.boats;

import com.richardsenger.piratesnships.hazards.waves.WaveField;
import com.richardsenger.piratesnships.hazards.waves.client.surface.SurfaceRules;

/**
 * How vanilla boats and floating items ride the simulated waves (WAV6, docs/design.md §5.4). Pure: no world access, so
 * the server, the client that drives a boat and the tests share one rule.
 *
 * <h2>Where</h2>
 * Only over the <b>open sea</b>: the column under the entity is a sea cell of {@link SurfaceRules#isSeaCell} (still water
 * at sea level − 1 with air above, the drawn sea's rule), not inside a dry hull, and the entity's bottom lies within
 * {@link #BAND} blocks of the largest crest above or below the still sea ({@link #inBand}). Nothing in lakes and rivers
 * off sea level, in a one-block-deep pool (its fade is 0), on a deck, a pier or in a hull. Everything else is vanilla.
 *
 * <h2>Offset</h2>
 * The still-water level rises by {@code fade · h}, with {@code h} the wave height at the entity from the level's point
 * lookup ({@code SeaStates#heightAt} on the server, {@code ClientWaves#heightAt} on the client, both
 * {@link WaveField#heightAt}: whatever anchor rule the field uses, so a boat follows it) and {@code fade} the drawn sea's
 * depth fade ({@link SurfaceRules#depthFade} at the default {@code surface_full_depth}, {@link #FULL_DEPTH}: a fixed
 * value rather than the client option, so the server and the driving client compute the same level). A beach does not
 * lift a boat: one block of water fades the waves out completely.
 *
 * <h2>Carry</h2>
 * Vanilla's buoyancy in {@code Boat#floatBoat} pulls a boat toward the water level and damps its vertical speed by
 * {@code 0.75} each tick. Against a moving surface the damping also brakes the surface's own speed, so the boat lags a
 * rising crest by more than its height and goes under, and falls behind a sinking trough (measured in
 * {@code BoatWaveRuleTest}). {@link #carry} makes the damping act on the speed relative to the surface:
 * {@code v' = 0.75 (v − u + …) + u = vanilla + 0.25 u}. Items, whose vanilla buoyancy is far too weak to follow a wave,
 * get a kinematic follow instead ({@link #itemVelocity}).
 */
public final class BoatWaveRule {

    /** Depth from which the full wave height applies: the default of the client's {@code surface_full_depth}. */
    public static final int FULL_DEPTH = 6;
    /** Blocks beyond the largest crest (above) and trough (below) within which the rule acts on an entity's bottom. */
    public static final double BAND = 1.0;
    /** Share of the surface's vertical speed vanilla's damping takes away per tick: {@code 1 − 0.75}. */
    public static final double CARRY = 0.25;
    /** Depth of a floating item's bottom below the surface (vanilla floats an item until 0.1 of it is wet). */
    public static final double ITEM_DRAFT = 0.1;
    /** Share of an item's distance to its float level made up per tick. */
    public static final double ITEM_PULL = 0.3;
    /** Largest correction speed of a floating item [blocks per tick]. */
    public static final double ITEM_MAX_PULL = 0.15;
    /** An item more than this below the surface is left to vanilla (it sank, or is still falling in) [blocks]. */
    public static final double ITEM_CAPTURE_BELOW = 1.0;
    /** An item more than this above the surface falls freely [blocks]. */
    public static final double ITEM_CAPTURE_ABOVE = 0.1;

    private BoatWaveRule() {
    }

    /** The depth fade of a column with {@code depth} water blocks (0: not a sea cell), the drawn sea's rule. */
    public static double fade(int depth) {
        return SurfaceRules.cellFade(depth, FULL_DEPTH);
    }

    /**
     * Whether the rule acts at all.
     *
     * @param enabled the toggle ({@code waves.boats} or {@code waves.floating_items})
     * @param field   the level's sea this tick
     * @param depth   water blocks of the column, counted down from sea level − 1 (0: not a sea cell)
     * @param dry     the column's surface lies inside a dry hull
     * @param resting the entity stands on something at or above the still sea (a deck, a pier, the beach)
     */
    public static boolean applies(boolean enabled, WaveField field, int depth, boolean dry, boolean resting) {
        return enabled && !field.isFlat() && fade(depth) > 0.0 && !dry && !resting;
    }

    /** Whether an entity's bottom {@code minY} lies near enough to the moving sea around {@code stillTop}. */
    public static boolean inBand(double minY, double stillTop, WaveField field) {
        double reach = field.maxHeight() + BAND;
        return minY >= stillTop - reach && minY <= stillTop + reach;
    }

    /** A point lookup of the sea's height: {@code SeaStates#heightAt} or {@code ClientWaves#heightAt}. */
    @FunctionalInterface
    public interface Heights {
        double height(double x, double z, double t);
    }

    /** A point lookup of the sea's slope {@code {∂/∂x, ∂/∂z}}: {@code SeaStates#slopeAt} or {@code ClientWaves#slopeAt}. */
    @FunctionalInterface
    public interface Slopes {
        double[] slope(double x, double z, double t);
    }

    /** The rise of the water level at {@code (x, z)} and time {@code t}: {@code fade · h}. */
    public static double offset(Heights sea, double x, double z, double t, double fade) {
        return fade == 0.0 ? 0.0 : fade * sea.height(x, z, t);
    }

    /** The surface's rise over the coming tick at {@code (x, z)} [blocks per tick]. */
    public static double speed(Heights sea, double x, double z, double t, double fade) {
        return offset(sea, x, z, t + 1.0, fade) - offset(sea, x, z, t, fade);
    }

    /** The surface's gradient {@code {∂/∂x, ∂/∂z}} at {@code (x, z)}, faded. */
    public static double[] slope(Slopes sea, double x, double z, double t, double fade) {
        double[] g = sea.slope(x, z, t).clone();
        g[0] *= fade;
        g[1] *= fade;
        return g;
    }

    /** A boat's vertical speed after vanilla's buoyancy, with the damping made relative to a surface rising at {@code u}. */
    public static double carry(double vy, double u) {
        return vy + CARRY * u;
    }

    /** Whether an item's bottom {@code minY} is near enough to the surface {@code surfaceY} to float on it. */
    public static boolean itemCaptured(double minY, double surfaceY) {
        return minY <= surfaceY + ITEM_CAPTURE_ABOVE && minY >= surfaceY - ITEM_CAPTURE_BELOW;
    }

    /** The vertical speed of a floating item: the surface's speed plus a capped pull toward its float level. */
    public static double itemVelocity(double minY, double surfaceY, double u) {
        double pull = (surfaceY - ITEM_DRAFT - minY) * ITEM_PULL;
        return u + Math.max(-ITEM_MAX_PULL, Math.min(ITEM_MAX_PULL, pull));
    }

    /**
     * The hull tilt the slope {@code (sx, sz)} gives, limited to {@code maxDegrees}: the world-space normal
     * {@code (−sx, 1, −sz)} turned back toward up until its angle from up is at most the limit. Returns the normal
     * as {@code {x, y, z}}, unit length.
     */
    public static double[] tiltNormal(double sx, double sz, double maxDegrees) {
        double s = Math.sqrt(sx * sx + sz * sz);
        double max = Math.tan(Math.toRadians(Math.max(0.0, maxDegrees)));
        if (s > max && s > 0.0) {
            sx *= max / s;
            sz *= max / s;
        }
        double len = Math.sqrt(sx * sx + 1.0 + sz * sz);
        return new double[] {-sx / len, 1.0 / len, -sz / len};
    }
}

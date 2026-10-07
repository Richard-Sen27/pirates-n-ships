package com.richardsenger.piratesnships.combat.grapple;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure rules of the grappling hook (docs/design.md §8.3, §8.4): what a hit does, when the rope lets go, and how hard a
 * taut rope pulls. No world access; tested in JUnit.
 */
public final class GrappleRules {

    /** Extension beyond the rest length [blocks] over which the pull ramps from 0 to its full strength. */
    public static final double RAMP = 1.0;

    private GrappleRules() {
    }

    /** What a hook does with a hit. */
    public enum HitKind {
        /** A block of a ship other than the thrower's: the hook latches there. */
        LATCH,
        /** A block of the ship the thrower stands on: no latch. */
        OWN_SHIP,
        /** A world block (land, a quay, the sea floor): no latch. */
        LAND,
        /** An entity: it takes {@code grapple.entity_damage}, no latch. */
        ENTITY
    }

    /** A block hit: latch only onto a ship that is not the thrower's own. */
    public static HitKind blockHit(UUID hitShip, UUID throwerShip) {
        if (hitShip == null) {
            return HitKind.LAND;
        }
        return hitShip.equals(throwerShip) ? HitKind.OWN_SHIP : HitKind.LATCH;
    }

    /** Why a hook is let go, or {@link #NONE}. */
    public enum Release {
        NONE,
        /** {@code grapple.enabled} was switched off. */
        DISABLED,
        /** The thrower is gone, dead or in another dimension. */
        OWNER_GONE,
        /** The ship the hook hangs on is gone (sunk, disassembled, unloaded). */
        SHIP_GONE,
        /** The thrower is farther from the hook than the rope is long: the rope snaps. */
        TOO_FAR
    }

    /**
     * Whether a hook is let go this tick. Checked in this order: config, thrower, ship (latched hooks only), distance.
     *
     * @param ownerDistance distance from the thrower to the hook [blocks]
     */
    public static Release release(boolean enabled, boolean ownerPresent, boolean latched, boolean shipPresent,
                                  double ownerDistance, double maxRopeLength) {
        if (!enabled) {
            return Release.DISABLED;
        }
        if (!ownerPresent) {
            return Release.OWNER_GONE;
        }
        if (latched && !shipPresent) {
            return Release.SHIP_GONE;
        }
        return latched && ownerDistance > maxRopeLength ? Release.TOO_FAR : Release.NONE;
    }

    /** A rope that snaps loses the hook only when configured so; every other release returns it. */
    public static boolean hookReturned(Release reason, boolean ropeBreaksLoseHook) {
        return reason != Release.TOO_FAR || !ropeBreaksLoseHook;
    }

    /**
     * The rope's holding length: {@code holdDistance} plus a dead band {@code slack} in which the rope holds without
     * pulling. Hulls that touch (or that collision keeps apart) before the rope's ends reach {@code holdDistance} would
     * otherwise keep a small residual pull and a taut rope forever. Pass the result as {@code restLength} to
     * {@link #tension} and {@link #taut}; the ramp starts at this length, so from farther out the pull is unchanged.
     */
    public static double holdLength(double holdDistance, double slack) {
        return holdDistance + Math.max(0.0, slack);
    }

    /**
     * Rope tension [kpg·m/s²]: zero up to the rest length (a rope never pushes), then rising linearly over
     * {@link #RAMP} blocks to {@code maxForce}, minus damping times the closing speed (plus when the ends separate),
     * clamped to {@code [0, maxForce]}.
     *
     * @param distance      distance between the rope's ends [blocks]
     * @param restLength    length at which the rope goes slack [blocks]
     * @param extensionRate rate at which the distance grows [m/s], negative while the ends approach
     * @param maxForce      full pull [kpg·m/s²]
     * @param damping       [kpg/s]
     */
    public static double tension(double distance, double restLength, double extensionRate, double maxForce, double damping) {
        if (!(distance > restLength) || maxForce <= 0) {
            return 0.0;
        }
        double spring = maxForce * Math.min(1.0, (distance - restLength) / RAMP);
        double t = spring + damping * extensionRate;
        return Math.max(0.0, Math.min(maxForce, t));
    }

    /** Substeps the rope must have pulled, and must have pulled without progress, before it counts as stalled. */
    public static final int STALL_SUBSTEPS = 20;
    /** A distance decrease [blocks] smaller than this is no progress. */
    public static final double STALL_EPSILON = 0.01;
    /** Separation speed [m/s] below which the ends still count as closing (never stalled). */
    public static final double CLOSING_SPEED = -0.05;
    /** Margin [blocks] beyond the hold length within which a stall can start, and beyond which holding ends. */
    public static final double HOLD_HYSTERESIS = 1.0;

    /**
     * The rope's holding state, updated once per physics substep (pure, one per hook). Hulls touch at a rope length
     * that depends on their shape, so the rope also holds, with no pull, once it has stopped making progress: when it
     * has pulled for at least {@link #STALL_SUBSTEPS} substeps, the distance has not dropped by more than
     * {@link #STALL_EPSILON} for the last {@link #STALL_SUBSTEPS} of them, the ends are not closing (separation speed
     * above {@link #CLOSING_SPEED}) and the distance is within {@link #HOLD_HYSTERESIS} of the hold length (a stall far
     * out, e.g. a heavy ship slow to start, keeps pulling). Holding ends only when the distance grows beyond the hold
     * length plus {@link #HOLD_HYSTERESIS}, so the ships do not oscillate between pulling and holding.
     */
    public static final class Holding {
        private boolean holding;
        private int pulling;
        private int sinceProgress;
        private double best = Double.POSITIVE_INFINITY;

        /**
         * One substep. Returns true while the rope holds (no pull).
         *
         * @param distance        distance between the rope's ends [blocks]
         * @param restLength      the hold length ({@link #holdLength}) [blocks]
         * @param separationSpeed rate at which the distance grows [m/s]
         */
        public boolean update(double distance, double restLength, double separationSpeed) {
            if (holding) {
                if (!(distance > restLength + HOLD_HYSTERESIS)) {
                    return true;
                }
                reset();
            }
            if (!(distance > restLength)) {
                // slack inside the dead band: the rope is not pulling
                pulling = 0;
                sinceProgress = 0;
                best = distance;
                return false;
            }
            pulling++;
            if (distance < best - STALL_EPSILON) {
                best = distance;
                sinceProgress = 0;
            } else {
                sinceProgress++;
            }
            if (pulling >= STALL_SUBSTEPS && sinceProgress >= STALL_SUBSTEPS && separationSpeed > CLOSING_SPEED
                    && distance <= restLength + HOLD_HYSTERESIS) {
                holding = true;
            }
            return holding;
        }

        public boolean holding() {
            return holding;
        }

        public void reset() {
            holding = false;
            pulling = 0;
            sinceProgress = 0;
            best = Double.POSITIVE_INFINITY;
        }
    }

    /** True when the rope is taut: longer than the rest length and there is a force to pull with. */
    public static boolean taut(double distance, double restLength, double maxForce) {
        return distance > restLength && maxForce > 0;
    }

    /** The rope runs out of length during the flight: the hook drops. */
    public static boolean ropeRunsOut(double ownerDistance, double maxRopeLength) {
        return ownerDistance > maxRopeLength;
    }

    /** Same-ship check that tolerates nulls (land on either end is never "the same ship"). */
    public static boolean sameShip(UUID a, UUID b) {
        return a != null && Objects.equals(a, b);
    }
}

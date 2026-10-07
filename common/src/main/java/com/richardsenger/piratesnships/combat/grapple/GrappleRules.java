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

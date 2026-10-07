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
        /** A block of a ship other than the near end's: the hook latches there (and hauls, {@link #haul}). */
        LATCH,
        /** A block of the near end's own ship (GR4, {@code latch_own_ship}): it latches, a line without force. */
        LATCH_OWN_SHIP,
        /** A world block (GR4, {@code latch_world_blocks}): it latches at the world position. */
        LATCH_WORLD,
        /** A block of the near end's own ship with {@code latch_own_ship} off: no latch. */
        OWN_SHIP,
        /** A world block (land, a quay, the sea floor) with {@code latch_world_blocks} off: no latch. */
        LAND,
        /** A block the hook slips off (tag {@code pirates_n_ships:grapple_slips}: leaves, glass panes): no latch. */
        SLIP,
        /** An entity: it takes {@code grapple.entity_damage}, no latch. */
        ENTITY
    }

    /**
     * A block hit (GR4: any solid surface catches): {@code hitShip} is the ship of the hit block (null: a world block),
     * {@code nearShip} the ship at the rope's near end (the thrower's, or the tied ring's; null: land), {@code catches}
     * false for a block the hook slips off. The toggles switch off latching on the own ship and on world blocks.
     */
    public static HitKind blockHit(UUID hitShip, UUID nearShip, boolean catches, boolean latchOwnShip, boolean latchWorld) {
        if (!catches) {
            return HitKind.SLIP;
        }
        if (hitShip == null) {
            return latchWorld ? HitKind.LATCH_WORLD : HitKind.LAND;
        }
        if (hitShip.equals(nearShip)) {
            return latchOwnShip ? HitKind.LATCH_OWN_SHIP : HitKind.OWN_SHIP;
        }
        return HitKind.LATCH;
    }

    /** Whether a hit latches the hook. */
    public static boolean latches(HitKind kind) {
        return kind == HitKind.LATCH || kind == HitKind.LATCH_OWN_SHIP || kind == HitKind.LATCH_WORLD;
    }

    /** What a latched rope pulls (GR4). */
    public enum Haul {
        /** Hook on one ship, near end on another: both ships are hauled together ({@code haul_force}). */
        SHIPS,
        /** Hook on a ship, near end on land: the hooked ship is pulled toward the shore ({@code shore_haul_force}). */
        SHORE,
        /** Hook on a world block, near end on a ship: that ship is hauled toward the block, a kedge ({@code haul_force}). */
        KEDGE,
        /** Both ends on the same body (one ship, or the world): a line to slide along, no force. */
        NONE
    }

    /**
     * The force a latched rope applies: {@code farShip} is the ship the hook holds (null: a world block),
     * {@code nearShip} the ship at the rope's near end (null: land).
     */
    public static Haul haul(UUID farShip, UUID nearShip) {
        if (farShip == null) {
            return nearShip == null ? Haul.NONE : Haul.KEDGE;
        }
        if (nearShip == null) {
            return Haul.SHORE;
        }
        return farShip.equals(nearShip) ? Haul.NONE : Haul.SHIPS;
    }

    /** Why a hook is let go, or {@link #NONE}. */
    public enum Release {
        NONE,
        /** {@code grapple.enabled} was switched off. */
        DISABLED,
        /** The thrower is gone, dead or in another dimension. */
        OWNER_GONE,
        /** The ship the hook hangs on is gone (sunk, disassembled, unloaded), or the world block it holds was broken (GR4). */
        SHIP_GONE,
        /** The mooring ring the hook is latched on, or the rope is tied to, was broken (GR1). */
        RING_GONE,
        /** The rope's near end (the thrower, or the ring it is tied to) is farther from the hook than the rope holds: it snaps. */
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

    /**
     * Like {@link #release(boolean, boolean, boolean, boolean, double, double)} with the mooring rings (GR1), checked in
     * this order: config, thrower, ship (latched hooks only), rings, distance. {@code nearEndDistance} is measured from
     * the rope's near end (the thrower, or the ring it is tied to) and {@code breakLength} comes from
     * {@link #breakLength}. A tied rope's thrower may stand anywhere, so the caller passes {@code ownerPresent} true for
     * it as long as the thrower is known.
     *
     * @param ringsIntact false when a ring the hook is latched on, or the rope is tied to, is gone
     */
    public static Release release(boolean enabled, boolean ownerPresent, boolean latched, boolean shipPresent,
                                  boolean ringsIntact, double nearEndDistance, double breakLength) {
        Release r = release(enabled, ownerPresent, latched, shipPresent, 0.0, breakLength);
        if (r != Release.NONE) {
            return r;
        }
        if (!ringsIntact) {
            return Release.RING_GONE;
        }
        return latched && nearEndDistance > breakLength ? Release.TOO_FAR : Release.NONE;
    }

    // ------------------------------------------------------------------ mooring rings (GR1)

    /**
     * Distance at which the rope snaps: its length, times {@code ringHoldMultiplier} (at least 1) when the hook is
     * latched on a mooring ring. The rope's break rule is a distance (the hook tears loose once the ends are farther
     * apart than the rope is long), so "a ring holds {@code ring_hold_multiplier} times the normal break tension" is
     * this length.
     */
    public static double breakLength(double ropeLength, boolean onRing, double ringHoldMultiplier) {
        return onRing ? ropeLength * Math.max(1.0, ringHoldMultiplier) : ropeLength;
    }

    /** Shortest distance from point p to the segment a-b (to a when a equals b). */
    public static double segmentDistance(double ax, double ay, double az, double bx, double by, double bz,
                                         double px, double py, double pz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 < 1.0e-12 ? 0.0 : ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx - px, cy = ay + t * dy - py, cz = az + t * dz - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /** A flying hook whose path this tick passes {@code distance} from a ring is caught by it. */
    public static boolean ringCatches(double distance, double catchRadius) {
        return catchRadius > 0 && distance <= catchRadius;
    }

    /** Where the rope's near end is. */
    public enum NearEnd {
        /** In the thrower's hand: the thrower's ship (its block nearest the hook) hauls, or the thrower stands on land. */
        THROWER,
        /** Tied to a mooring ring: the ring's ship hauls from the ring, or a ring on land holds the rope. */
        RING
    }

    public static NearEnd nearEnd(boolean tiedToRing) {
        return tiedToRing ? NearEnd.RING : NearEnd.THROWER;
    }

    /** The ship that hauls at the near end: the ring's ship when tied (null: a ring on land), else the thrower's. */
    public static UUID haulingShip(NearEnd nearEnd, UUID ringShip, UUID throwerShip) {
        return nearEnd == NearEnd.RING ? ringShip : throwerShip;
    }

    /** Whether the rope can be tied to a ring. */
    public enum Tie { OK, NO_HOOK, SAME_SHIP, TOO_FAR }

    /**
     * Tying the near end to a ring: needs a hook out, a ring not on the ship the hook is latched on (the rope would
     * run from a ship to itself), and a ring within the rope's length of the hook.
     *
     * @param hookShip ship the hook is latched on, null while it flies or lies on land
     */
    public static Tie tie(boolean hookOut, UUID ringShip, UUID hookShip, double ringToHook, double ropeLength) {
        if (!hookOut) {
            return Tie.NO_HOOK;
        }
        if (sameShip(ringShip, hookShip)) {
            return Tie.SAME_SHIP;
        }
        return ringToHook > ropeLength ? Tie.TOO_FAR : Tie.OK;
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
    /** Margin [blocks] beyond the hold length within which a stall can start, and beyond which holding ends. */
    public static final double HOLD_HYSTERESIS = 1.0;

    /**
     * The rope's holding state, updated once per physics substep (pure, one per hook). Hulls touch at a rope length
     * that depends on their shape, so the rope also holds, with no pull, once it has stopped making progress: when it
     * has pulled for at least {@link #STALL_SUBSTEPS} substeps, the distance has not dropped by more than
     * {@link #STALL_EPSILON} for the last {@link #STALL_SUBSTEPS} of them, and the distance is within
     * {@link #HOLD_HYSTERESIS} of the hold length (a stall far out, e.g. a heavy ship slow to start, keeps pulling).
     * Holding ends only when the distance grows beyond the hold length plus {@link #HOLD_HYSTERESIS}, so the ships do
     * not oscillate between pulling and holding.
     *
     * <p>Progress is judged from the distance alone, not from the bodies' velocities: with the hulls in contact the
     * physics engine keeps reporting a closing velocity (about 0.12 to 0.17 m/s, measured in G11) produced by the rope
     * force each substep, which the contact solver then cancels, so the distance does not change.
     */
    public static final class Holding {
        private boolean holding;
        private int pulling;
        private int sinceProgress;
        private double best = Double.POSITIVE_INFINITY;

        /**
         * One substep. Returns true while the rope holds (no pull).
         *
         * @param distance   distance between the rope's ends [blocks]
         * @param restLength the hold length ({@link #holdLength}) [blocks]
         */
        public boolean update(double distance, double restLength) {
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
            if (pulling >= STALL_SUBSTEPS && sinceProgress >= STALL_SUBSTEPS && distance <= restLength + HOLD_HYSTERESIS) {
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

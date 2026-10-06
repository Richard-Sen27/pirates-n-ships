package com.richardsenger.piratesnships.sailing.anchor;

/**
 * Pure travel rule of the anchor on its chain (docs/design.md §5.3).
 *
 * <p>The anchor runs out (and is heaved in) at a constant chain speed, so a trip takes {@code distance / speed},
 * clamped to {@code [minTicks, maxTicks]}. The anchor's position along the chain is the holding factor of
 * {@code AnchorState}: 0 = at the hawse, 1 = on the ground. The state ramps by {@code 1 / ticks} per tick, so the
 * anchor moves at {@code distance / ticks} blocks per tick and the ship holds fully on the tick the anchor lands.
 */
public final class AnchorTravel {

    /** Height of the anchor model from crown to ring, in blocks (the hawse is the ring of the stowed anchor). */
    public static final double HEIGHT = 2.0;

    private AnchorTravel() {
    }

    /** Ticks for a trip of {@code distance} blocks at {@code blocksPerSecond}, clamped. */
    public static int ticks(double distance, double blocksPerSecond, int minTicks, int maxTicks) {
        int lo = Math.max(1, minTicks);
        int hi = Math.max(lo, maxTicks);
        if (!(blocksPerSecond > 0.0)) {
            return hi;
        }
        double t = Math.ceil(Math.max(0.0, distance) / blocksPerSecond * 20.0 - 1e-9);
        return (int) Math.max(lo, Math.min(hi, t));
    }

    /** Distance the anchor travels: from its stowed crown (hawse minus {@link #HEIGHT}) down to the ground. */
    public static double distance(double hawseY, double groundY) {
        return Math.max(0.0, hawseY - HEIGHT - groundY);
    }

    /** Linear interpolation along the chain: {@code f = 0} at {@code from}, 1 at {@code to}. */
    public static double lerp(double from, double to, double f) {
        return from + (to - from) * Math.min(Math.max(f, 0.0), 1.0);
    }
}

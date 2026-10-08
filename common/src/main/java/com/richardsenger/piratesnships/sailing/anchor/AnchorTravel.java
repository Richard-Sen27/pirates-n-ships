package com.richardsenger.piratesnships.sailing.anchor;

/**
 * Dimensions of the anchor (docs/design.md §5.2). Since AN2a the anchor's travel is physical ({@link AnchorMotion}),
 * not a timed ramp along the chain.
 */
public final class AnchorTravel {

    /** Height of the anchor model from crown to ring, in blocks (the hawse is the ring of the stowed anchor). */
    public static final double HEIGHT = 2.0;

    private AnchorTravel() {
    }

    /** Depth the anchor falls from the hawse to the ground: from its stowed crown (hawse minus {@link #HEIGHT}). */
    public static double distance(double hawseY, double groundY) {
        return Math.max(0.0, hawseY - HEIGHT - groundY);
    }
}

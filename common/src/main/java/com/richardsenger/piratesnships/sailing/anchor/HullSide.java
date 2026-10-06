package com.richardsenger.piratesnships.sailing.anchor;

/**
 * Pure rule for where a capstan's anchor hangs when stowed (docs/design.md §5.3).
 *
 * <p>From the capstan, look across the ship (perpendicular to the bow) to both sides. On each side the hull face is
 * just beyond the <b>outermost</b> ship cell in that row, counting the deck layer (one below the capstan) and the
 * capstan's own layer (bulwarks, rails), up to {@code reach} cells out. Gaps such as an open hatch don't end the
 * search. The anchor hangs on the nearer side; on a tie it hangs to starboard. It hangs in the first cell outside
 * the face, pulled {@link #HULL_GAP} towards the hull, with its ring {@link #RING_BELOW_DECK} below the deck's top
 * surface (the capstan's block y).
 */
public final class HullSide {

    /** How far the anchor's centre line sits from the hull face, in blocks. */
    public static final double HULL_GAP = 0.4;
    /** How far below the deck surface the ring hangs, in blocks. */
    public static final double RING_BELOW_DECK = 0.25;

    /** Ship cells relative to the capstan's block. */
    @FunctionalInterface
    public interface Cells {
        boolean isShip(int dx, int dy, int dz);
    }

    /**
     * @param sideX    unit step across the ship towards the chosen side
     * @param sideZ    unit step across the ship towards the chosen side
     * @param distance cells from the capstan to the first cell outside the hull on that side (≥ 1)
     * @param starboard whether the chosen side is starboard
     */
    public record Placement(int sideX, int sideZ, int distance, boolean starboard) {

        /** The hawse (ring of the stowed anchor), relative to the capstan block's min corner. */
        public double[] hawseOffset() {
            double out = distance - 0.5 + HULL_GAP;
            return new double[] {0.5 + sideX * out, -RING_BELOW_DECK, 0.5 + sideZ * out};
        }
    }

    private HullSide() {
    }

    /** Starboard of a bow direction (right hand when facing the bow, y up): {@code (-bowDz, bowDx)}. */
    public static int[] starboard(int bowDx, int bowDz) {
        return new int[] {-bowDz, bowDx};
    }

    public static Placement find(int bowDx, int bowDz, int reach, Cells cells) {
        int[] s = starboard(bowDx, bowDz);
        int dStar = distance(s[0], s[1], reach, cells);
        int dPort = distance(-s[0], -s[1], reach, cells);
        return dPort < dStar ? new Placement(-s[0], -s[1], dPort, false) : new Placement(s[0], s[1], dStar, true);
    }

    static int distance(int sx, int sz, int reach, Cells cells) {
        int outer = 0;
        for (int k = 1; k <= reach; k++) {
            if (cells.isShip(sx * k, -1, sz * k) || cells.isShip(sx * k, 0, sz * k)) {
                outer = k;
            }
        }
        return outer + 1;
    }
}

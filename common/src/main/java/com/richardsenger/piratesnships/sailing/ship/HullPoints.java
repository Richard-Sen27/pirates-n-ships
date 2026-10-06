package com.richardsenger.piratesnships.sailing.ship;

import org.joml.Vector3d;

/** Pure geometry of attachment points on a ship's plot box. */
public final class HullPoints {

    private HullPoints() {
    }

    /**
     * Rudder position in plot coordinates: the middle of the stern face of the plot box (the face opposite the bow),
     * centered in beam, at the center of the bottom block layer (the keel).
     *
     * @param bounds {minX, minY, minZ, maxX, maxY, maxZ} of the plot box (inclusive block coordinates)
     */
    public static Vector3d rudderPlot(BowFrame bow, int[] bounds, Vector3d dest) {
        double midX = (bounds[0] + bounds[3] + 1) * 0.5;
        double midZ = (bounds[2] + bounds[5] + 1) * 0.5;
        double y = bounds[1] + 0.5;
        if (bow.dx() > 0) {
            return dest.set(bounds[0], y, midZ);
        }
        if (bow.dx() < 0) {
            return dest.set(bounds[3] + 1, y, midZ);
        }
        if (bow.dz() > 0) {
            return dest.set(midX, y, bounds[2]);
        }
        return dest.set(midX, y, bounds[5] + 1);
    }
}

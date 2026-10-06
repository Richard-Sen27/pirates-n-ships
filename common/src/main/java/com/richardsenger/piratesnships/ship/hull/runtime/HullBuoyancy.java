package com.richardsenger.piratesnships.ship.hull.runtime;

import com.richardsenger.piratesnships.ship.hull.HullVec;
import com.richardsenger.piratesnships.ship.hull.flooding.FloodReport;
import java.util.ArrayList;
import java.util.List;

/**
 * The buoyancy correction of docs/design.md §4.5, pure. Sable floats solid hull blocks natively (10.5 × overlap volume
 * per block, docs/sable-notes.md §4.1) but ignores the air inside the hull. We add:
 * <ul>
 *   <li><b>dry lift</b>: {@code forcePerBlock · dryScale · submergedDryVolume}, along world up, at the dry centroid;</li>
 *   <li><b>flood weight</b>: {@code forcePerBlock · floodScale · floodVolume}, along world down, at the flood centroid.</li>
 * </ul>
 * Forces come out in the ship (plot) frame, which is the frame Sable's queued force groups take; points are plot
 * positions. Multiply by the substep length to get the impulse Sable expects.
 */
public final class HullBuoyancy {

    /** Sable's native float force per block of displaced liquid (rust {@code buoyancy.rs#do_float}). */
    public static final double SABLE_FLOAT_FORCE = 10.5;

    /**
     * @param dryEnabled    apply the dry-volume lift
     * @param dryScale      multiplier on the dry lift
     * @param floodEnabled  apply the flood-water weight
     * @param floodScale    multiplier on the flood weight
     * @param forcePerBlock force of one block of water (lift of a displaced block, weight of a flooded one)
     */
    public record Params(boolean dryEnabled, double dryScale, boolean floodEnabled, double floodScale, double forcePerBlock) {
        public static final Params DEFAULTS = new Params(true, 1.0, true, 1.0, SABLE_FLOAT_FORCE);
    }

    /** One force in the ship frame at a plot position. */
    public record PointForce(HullVec point, HullVec force) {
    }

    private HullBuoyancy() {
    }

    /**
     * @param report  the flooding report for the current sea level
     * @param localUp world up in the ship frame (unit length)
     */
    public static List<PointForce> forces(FloodReport report, HullVec localUp, Params p) {
        List<PointForce> out = new ArrayList<>(2);
        if (p.dryEnabled() && report.dryCentroid() != null && report.submergedDryVolume() > 0) {
            double f = p.forcePerBlock() * p.dryScale() * report.submergedDryVolume();
            out.add(new PointForce(report.dryCentroid(), scale(localUp, f)));
        }
        if (p.floodEnabled() && report.floodCentroid() != null && report.floodVolume() > 0) {
            double f = -p.forcePerBlock() * p.floodScale() * report.floodVolume();
            out.add(new PointForce(report.floodCentroid(), scale(localUp, f)));
        }
        return out;
    }

    private static HullVec scale(HullVec v, double s) {
        return new HullVec(v.x() * s, v.y() * s, v.z() * s);
    }
}

package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Force of one sail (docs/design.md §5.2).
 *
 * <h2>Formula</h2>
 * <ol>
 *   <li>Center of effort {@code r = position + UP · centerOfEffortHeight} (ship frame, relative to the COM).</li>
 *   <li>Apparent wind {@code a = trueWind − velocityAt(r)} (world), rotated into the ship frame, keeping only the
 *       component in the deck plane (x, z).</li>
 *   <li>Apparent wind angle {@code β} = angle between the bow and where the apparent wind comes from, 0..180°. The
 *       leeward side is the side the wind blows toward.</li>
 *   <li>{@code F = scale · |a| · area · trim · (drive(β) · FORWARD + side(β) · LEEWARD)}. Linear in wind speed, as the
 *       spec's "wind strength × area × trim × efficiency" says, which keeps top speeds proportional to the wind.</li>
 *   <li>Torque {@code r × F}: the side force acting above the COM heels the ship to leeward, and sails away from the
 *       COM along the hull yaw it.</li>
 * </ol>
 * The crew is assumed to set the sheets optimally, so the efficiency depends on {@code β} only. A ship moving downwind
 * at wind speed feels no apparent wind and gets no push.
 */
public final class SailForceModel {

    private static final double EPS = 1.0e-9;

    private SailForceModel() {
    }

    /**
     * @param sail      the sail
     * @param trueWind  true wind velocity, world frame [blocks/s]
     * @param ship      ship state
     * @param params    tuning
     * @param source    label for the result
     * @return the sail's force at its center of effort, ship frame
     */
    public static ForceContribution compute(SailInstance sail, Vector3dc trueWind, ShipState ship, SailingParams params,
                                            String source) {
        Vector3d point = new Vector3d(ShipFrame.UP).mul(sail.type().centerOfEffortHeight()).add(sail.position());
        double trim = sail.trim().factor(params);
        if (trim <= 0.0 || sail.area() <= 0.0) {
            return ForceContribution.zero(source, point);
        }
        Vector3d apparentWorld = new Vector3d(trueWind).sub(ship.velocityAt(point, new Vector3d()));
        Vector3d a = ship.toLocal(apparentWorld, new Vector3d());
        double fwd = a.dot(ShipFrame.FORWARD);
        double lat = a.dot(ShipFrame.PORT);
        double speed = Math.sqrt(fwd * fwd + lat * lat);
        if (speed < EPS) {
            return ForceContribution.zero(source, point);
        }
        // The wind comes from −a; β = angle between FORWARD and −a.
        double beta = Math.toDegrees(Math.atan2(Math.abs(lat), -fwd));
        double magnitude = params.sailForceScale() * speed * sail.area() * trim;
        double drive = sail.type().curve().drive(beta) * magnitude;
        double side = sail.type().curve().side(beta) * magnitude;
        // Leeward: the side the apparent wind blows toward (sign of its lateral component).
        double leewardSign = lat >= 0.0 ? 1.0 : -1.0;
        Vector3d force = new Vector3d(ShipFrame.FORWARD).mul(drive)
                .add(new Vector3d(ShipFrame.PORT).mul(side * leewardSign));
        return ForceContribution.atPoint(source, force, point);
    }
}

package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;

/**
 * Keel / lateral resistance (docs/design.md §5.3, docs/sable-notes.md §4.3). Sable's own water drag is the same in
 * every direction, so this adds an anisotropic drag in the ship frame, strong across the hull and weak along it.
 *
 * <ul>
 *   <li>Linear drag: {@code F = −mass · submerged · (c_long · v_fwd · FORWARD + c_lat · v_side · PORT)} using the
 *       ship-frame velocity of the keel center (rotation included). Vertical motion is left to Sable's buoyancy.
 *       Applied at {@link ShipState#keelCenter()} (below the COM), so it resists heel together with Sable's
 *       buoyancy torque and adds the heeling couple a real keel has.</li>
 *   <li>Yaw damping: lateral drag spread evenly along a hull of length {@code L} turning at {@code ω} integrates to a
 *       torque {@code −mass · submerged · c_lat · ω · L² / 12} (times {@code keelYawDragFactor}).</li>
 * </ul>
 * Mass-proportional, so the coefficients are decay rates [1/s]: with the defaults a ship loses sideways speed with a
 * time constant of 0.125 s and forward speed with one of 10 s.
 */
public final class KeelModel {

    private KeelModel() {
    }

    public static ForceContribution compute(ShipState ship, SailingParams p) {
        if (!p.keelEnabled() || ship.submergedFraction() <= 0.0) {
            return ForceContribution.zero("keel", ship.keelCenter());
        }
        double k = ship.mass() * ship.submergedFraction();
        Vector3d v = ship.toLocal(ship.velocityAt(ship.keelCenter(), new Vector3d()), new Vector3d());
        double vFwd = v.dot(ShipFrame.FORWARD);
        double vSide = v.dot(ShipFrame.PORT);
        Vector3d force = new Vector3d(ShipFrame.FORWARD).mul(-k * p.keelLongitudinalDrag() * vFwd)
                .add(new Vector3d(ShipFrame.PORT).mul(-k * p.keelLateralDrag() * vSide));
        Vector3d torque = ship.keelCenter().cross(force, new Vector3d());

        Vector3d w = ship.toLocal(ship.angularVelocity(), new Vector3d());
        double yawRate = w.dot(ShipFrame.UP);
        double length = ship.hullLength();
        double yawDamping = -k * p.keelLateralDrag() * p.keelYawDragFactor() * yawRate * length * length / 12.0;
        torque.add(new Vector3d(ShipFrame.UP).mul(yawDamping));
        return new ForceContribution("keel", force, ship.keelCenter(), torque);
    }
}

package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;

/**
 * Roll and pitch damping of the hull in the water (docs/design.md §21, "undamped roll"). Sable's buoyancy rights a
 * heeled hull but nothing takes energy out of the rocking, so a floating ship rolled from side to side without end.
 * This adds a pure torque about the center of mass, in the {@link ShipFrame}, that opposes the roll rate (about
 * {@link ShipFrame#FORWARD}) and the pitch rate (about {@link ShipFrame#PORT}). Yaw is left to the keel
 * ({@link KeelModel}).
 *
 * <pre>
 *   τ_roll  = − c_roll  · s · m · B² / 12 · ω_roll
 *   τ_pitch = − c_pitch · s · m · L² / 12 · ω_pitch
 * </pre>
 * with {@code s} the submerged fraction, {@code m} the mass, {@code B} the beam and {@code L} the length of the hull.
 *
 * <p><b>Why {@code m · B² / 12}:</b> it is the moment of inertia of a slab of mass {@code m} and width {@code B} about
 * its long axis (a box hull has {@code m (B² + H²) / 12}), and it is the same sum the keel uses for its yaw damping
 * ({@code L² / 12}): water drag on the hull sides that grows with the distance from the axis. So the coefficients are
 * roughly <em>decay rates</em> [1/s] of the angular velocity and the same value works for a dinghy and a brig: a hull
 * twice as wide gets four times the torque for four times the inertia. The submerged fraction makes it vanish out of the
 * water and grow while the hull settles in.
 *
 * <p>The torque is always against the angular velocity on each axis, so it never adds energy
 * ({@code τ · ω ≤ 0}). It is not scaled by {@code sail_heel_factor}: that factor tames the heeling moment of sails and
 * keel, while damping only ever slows the motion down.
 */
public final class HullDampingModel {

    public static final String SOURCE = "hull_damping";

    /**
     * Damping tuning.
     *
     * @param enabled whether the damping is applied at all
     * @param roll    roll damping, roughly the decay rate of the roll rate at full immersion [1/s]
     * @param pitch   pitch damping, the same for the pitch rate [1/s]
     */
    public record Params(boolean enabled, double roll, double pitch) {

        /** The defaults. The server config declares its defaults from this instance. Chosen by measurement (F1). */
        public static final Params DEFAULTS = new Params(true, 1.5, 1.5);

        public Params {
            roll = Math.max(0.0, roll);
            pitch = Math.max(0.0, pitch);
        }
    }

    private HullDampingModel() {
    }

    /**
     * @param ship ship state (angular velocity in the world frame, mass, submerged fraction, length)
     * @param beam the hull's width across the bow axis [blocks]
     * @param p    tuning
     * @return a pure torque at the center of mass (force zero), labelled {@link #SOURCE}
     */
    public static ForceContribution compute(ShipState ship, double beam, Params p) {
        Vector3d origin = new Vector3d();
        if (!p.enabled() || ship.submergedFraction() <= 0.0) {
            return ForceContribution.zero(SOURCE, origin);
        }
        double k = ship.mass() * ship.submergedFraction() / 12.0;
        double b = Math.max(0.0, beam);
        double l = ship.hullLength();
        Vector3d w = ship.toLocal(ship.angularVelocity(), new Vector3d());
        double rollRate = w.dot(ShipFrame.FORWARD);
        double pitchRate = w.dot(ShipFrame.PORT);
        Vector3d torque = new Vector3d(ShipFrame.FORWARD).mul(-p.roll() * k * b * b * rollRate)
                .add(new Vector3d(ShipFrame.PORT).mul(-p.pitch() * k * l * l * pitchRate));
        return new ForceContribution(SOURCE, new Vector3d(), origin, torque);
    }
}

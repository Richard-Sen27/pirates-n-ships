package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Rudder force (docs/design.md §5.3, helm).
 *
 * <p>{@code F = −STARBOARD · rudderStrength · rudderForceFactor · mass · submerged · v_fwd · sin(δ)}, applied at the rudder position.
 * {@code δ} = rudder angle, positive = "turn to starboard", clamped to ±{@code maxRudderAngleDeg}; {@code v_fwd} = the
 * ship's speed along its bow axis. Pushing the stern to port swings the bow to starboard. The force (and so the yaw
 * torque) is zero at standstill, grows linearly with speed and reverses when moving astern, like a real rudder.
 *
 * <p>Mass-proportional so the turn rate does not depend on displacement: with yaw inertia ≈ m·L²/12 and the keel's
 * yaw damping (see {@link KeelModel}), the steady turn rate is ≈ {@code 12 · strength · factor · v · sin δ · lever / (L² · lateralDrag)}.
 * Linear in {@code v}, so the steady turning circle ({@code v / ω}) does not depend on the speed: a slow ship answers the
 * helm on the same circle, only more slowly, and a ship without way through the water gets no torque at all.
 * {@code rudderForceFactor} (SH2) is the turning authority: measured on the starter sloop, 3 turns a circle of 3.4 ship
 * lengths at full speed, 1 (milestone 3) one of 10.2.
 */
public final class RudderModel {

    private RudderModel() {
    }

    /**
     * @param angleDeg rudder angle [degrees], positive = turn to starboard
     * @param position rudder position relative to the COM, ship frame [blocks] (normally at the stern, {@code z < 0})
     */
    public static ForceContribution compute(double angleDeg, Vector3dc position, ShipState ship, SailingParams p) {
        double max = p.maxRudderAngleDeg();
        double delta = Math.toRadians(Math.min(Math.max(angleDeg, -max), max));
        double vFwd = ship.localVelocity(new Vector3d()).dot(ShipFrame.FORWARD);
        double magnitude = p.rudderStrength() * p.rudderForceFactor() * ship.mass() * ship.submergedFraction() * vFwd * Math.sin(delta);
        Vector3d force = new Vector3d(ShipFrame.PORT).mul(magnitude);
        return ForceContribution.atPoint("rudder", force, position);
    }
}

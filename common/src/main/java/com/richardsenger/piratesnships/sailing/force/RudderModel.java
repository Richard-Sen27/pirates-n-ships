package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Rudder force (docs/design.md §5.3, helm).
 *
 * <p>{@code F = −STARBOARD · rudderStrength · mass · submerged · v_fwd · sin(δ)}, applied at the rudder position.
 * {@code δ} = rudder angle, positive = "turn to starboard", clamped to ±{@code maxRudderAngleDeg}; {@code v_fwd} = the
 * ship's speed along its bow axis. Pushing the stern to port swings the bow to starboard. The force (and so the yaw
 * torque) is zero at standstill, grows linearly with speed and reverses when moving astern, like a real rudder.
 *
 * <p>Mass-proportional so the turn rate does not depend on displacement: with yaw inertia ≈ m·L²/12 and the keel's
 * yaw damping (see {@link KeelModel}), the steady turn rate is ≈ {@code 6 · strength · v · sin δ · lever / (L² · lateralDrag)}
 * — at the defaults, a 20-block hull at 5 blocks/s with full rudder turns about 12°/s.
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
        double magnitude = p.rudderStrength() * ship.mass() * ship.submergedFraction() * vFwd * Math.sin(delta);
        Vector3d force = new Vector3d(ShipFrame.PORT).mul(magnitude);
        return ForceContribution.atPoint("rudder", force, position);
    }
}

package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Polar curves (forward drive over wind angle) per sail type, for tests, tuning and a later HUD. Computed through
 * {@link SailForceModel} itself, for a ship at rest, a wind of 1 block/s, full trim, a force scale of 1 and the sail
 * at the center of mass, then divided by the sail area. So the values are the efficiency coefficients the model
 * really applies.
 */
public final class SailPolar {

    /** One point of a polar curve. {@code drive} is forward (+) or astern (−), {@code side} toward leeward. */
    public record Point(double windAngleDeg, double drive, double side) {
    }

    private SailPolar() {
    }

    /** The polar curve from 0° (head to wind) to 180° (dead downwind) in steps of {@code stepDeg}. */
    public static List<Point> curve(SailType type, double stepDeg) {
        List<Point> out = new ArrayList<>();
        for (double a = 0.0; a <= 180.0 + 1e-9; a += stepDeg) {
            out.add(at(type, Math.min(a, 180.0)));
        }
        return out;
    }

    /**
     * Drive and side force per unit area at a true wind angle off the bow. Positive angles = wind from starboard,
     * negative = from port; the result is the same (mirror image) except the side force is always reported as positive
     * toward leeward.
     */
    public static Point at(SailType type, double windAngleDeg) {
        SailingParams p = SailingParams.DEFAULTS.withSailForceScale(1.0);
        ShipState ship = ShipState.atRest(1.0, 10.0);
        SailInstance sail = new SailInstance(new SailType(type.id(), 1.0, 0.0, type.curve()), SailTrim.FULL, new Vector3d());
        ForceContribution c = SailForceModel.compute(sail, windFromAngle(windAngleDeg, 1.0), ship, p, type.id());
        double drive = c.force().dot(ShipFrame.FORWARD);
        double side = Math.abs(c.force().dot(ShipFrame.PORT));
        return new Point(windAngleDeg, drive, side);
    }

    /**
     * World wind velocity for a ship at rest with identity orientation: the wind comes from {@code angleDeg} off the
     * bow, positive toward starboard.
     */
    public static Vector3d windFromAngle(double angleDeg, double speed) {
        double r = Math.toRadians(angleDeg);
        Vector3d from = new Vector3d(ShipFrame.FORWARD).mul(Math.cos(r)).add(new Vector3d(ShipFrame.STARBOARD).mul(Math.sin(r)));
        return from.mul(-speed);
    }
}

package com.richardsenger.piratesnships.sailing.force;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A ship's physical state for one force evaluation. Immutable input; read from Sable by the ship integration.
 *
 * @param position          world position of the center of mass [blocks]
 * @param orientation       rotation <em>ship frame → world</em> (see {@link ShipFrame})
 * @param linearVelocity    velocity of the center of mass, world frame [blocks/s]
 * @param angularVelocity   angular velocity, world frame [rad/s]
 * @param mass              mass [kpg]; must be positive
 * @param submergedFraction how much of the hull is under water, 0..1 (scales keel and rudder)
 * @param hullLength        bow-to-stern length [blocks]; sets the keel's yaw damping
 * @param keelCenter        center of lateral resistance relative to the center of mass, ship frame [blocks]
 */
public record ShipState(Vector3dc position, Quaterniondc orientation, Vector3dc linearVelocity,
                        Vector3dc angularVelocity, double mass, double submergedFraction, double hullLength,
                        Vector3dc keelCenter) {

    public ShipState {
        if (!(mass > 0.0) || !Double.isFinite(mass)) {
            throw new IllegalArgumentException("Ship mass must be positive and finite: " + mass);
        }
        position = new Vector3d(position);
        orientation = new Quaterniond(orientation).normalize();
        linearVelocity = new Vector3d(linearVelocity);
        angularVelocity = new Vector3d(angularVelocity);
        submergedFraction = Math.min(Math.max(0.0, submergedFraction), 1.0);
        hullLength = Math.max(0.0, hullLength);
        keelCenter = new Vector3d(keelCenter);
    }

    /** A ship at rest at the origin, facing world +Z, with the keel center one block below the center of mass. */
    public static ShipState atRest(double mass, double hullLength) {
        return new ShipState(new Vector3d(), new Quaterniond(), new Vector3d(), new Vector3d(), mass, 1.0,
                hullLength, new Vector3d(0, -1, 0));
    }

    public ShipState withLinearVelocity(Vector3dc v) {
        return new ShipState(position, orientation, v, angularVelocity, mass, submergedFraction, hullLength, keelCenter);
    }

    public ShipState withAngularVelocity(Vector3dc w) {
        return new ShipState(position, orientation, linearVelocity, w, mass, submergedFraction, hullLength, keelCenter);
    }

    public ShipState withOrientation(Quaterniondc q) {
        return new ShipState(position, q, linearVelocity, angularVelocity, mass, submergedFraction, hullLength, keelCenter);
    }

    public ShipState withPosition(Vector3dc p) {
        return new ShipState(p, orientation, linearVelocity, angularVelocity, mass, submergedFraction, hullLength, keelCenter);
    }

    public ShipState withSubmergedFraction(double f) {
        return new ShipState(position, orientation, linearVelocity, angularVelocity, mass, f, hullLength, keelCenter);
    }

    /** Ship frame vector → world frame. */
    public Vector3d toWorld(Vector3dc local, Vector3d dest) {
        return orientation.transform(local, dest);
    }

    /** World frame vector → ship frame. */
    public Vector3d toLocal(Vector3dc world, Vector3d dest) {
        return orientation.transformInverse(world, dest);
    }

    /** World-frame velocity [blocks/s] of the hull point at {@code localOffset} (ship frame, relative to the COM). */
    public Vector3d velocityAt(Vector3dc localOffset, Vector3d dest) {
        Vector3d r = toWorld(localOffset, new Vector3d());
        return angularVelocity.cross(r, dest).add(linearVelocity);
    }

    /** Velocity of the center of mass in the ship frame [blocks/s]. */
    public Vector3d localVelocity(Vector3d dest) {
        return toLocal(linearVelocity, dest);
    }
}

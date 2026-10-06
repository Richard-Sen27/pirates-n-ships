package com.richardsenger.piratesnships.sailing.force;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * One contributor's <b>force</b> (not impulse) on the ship, everything in the {@link ShipFrame}.
 *
 * @param source a short label for debugging and the HUD, e.g. {@code "sail[0]:large_square"}, {@code "keel"}
 * @param force  force [kpg·blocks/s²]; multiply by the physics time step to get the impulse Sable expects
 * @param point  application point relative to the center of mass [blocks]
 * @param torque torque about the center of mass [kpg·blocks²/s²], including {@code point × force} and any pure
 *               torque (e.g. keel yaw damping)
 */
public record ForceContribution(String source, Vector3dc force, Vector3dc point, Vector3dc torque) {

    public ForceContribution {
        force = new Vector3d(force);
        point = new Vector3d(point);
        torque = new Vector3d(torque);
    }

    /** A force at a point; the torque is {@code point × force}. */
    public static ForceContribution atPoint(String source, Vector3dc force, Vector3dc point) {
        return new ForceContribution(source, force, point, point.cross(force, new Vector3d()));
    }

    public static ForceContribution zero(String source, Vector3dc point) {
        return new ForceContribution(source, new Vector3d(), point, new Vector3d());
    }

    public boolean isFinite() {
        return force.isFinite() && point.isFinite() && torque.isFinite();
    }
}

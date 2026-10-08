package com.richardsenger.piratesnships.hazards.waves.client;

import org.joml.Vector3dc;

/** The geometry of the camera sway (WV1). Pure. */
public final class CameraSway {

    private CameraSway() {
    }

    /**
     * How far the ship's up vector leans toward the viewer's right [degrees]: {@code asin(up · right)}, where right is
     * the horizontal right of a view at Minecraft yaw {@code yawRad} (yaw 0 looks toward +Z, so right is −X).
     * Positive when the deck dips on the viewer's right.
     */
    public static double heelAcrossViewDegrees(Vector3dc shipUp, double yawRad) {
        double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
        double d = shipUp.x() * rx + shipUp.z() * rz;
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d))));
    }
}

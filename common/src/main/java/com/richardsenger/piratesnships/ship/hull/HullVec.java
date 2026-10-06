package com.richardsenger.piratesnships.ship.hull;

/** A small immutable 3D vector in ship-local coordinates (kept free of Minecraft classes for pure tests). */
public record HullVec(double x, double y, double z) {

    public static final HullVec UP = new HullVec(0, 1, 0);

    public double dot(HullVec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public HullVec normalized() {
        double l = length();
        if (!(l > 1e-12)) {
            throw new IllegalArgumentException("Cannot normalize a zero vector");
        }
        return new HullVec(x / l, y / l, z / l);
    }

    /** Angle between two directions in degrees. */
    public double angleDegrees(HullVec o) {
        double c = normalized().dot(o.normalized());
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }
}

package com.richardsenger.piratesnships.combat.melee.geometry;

/** A tiny immutable 3D vector, so the hit geometry needs no Minecraft classes. */
public record Vec(double x, double y, double z) {

    public static final Vec ZERO = new Vec(0, 0, 0);

    public Vec add(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    public Vec sub(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    public Vec scale(double f) {
        return new Vec(x * f, y * f, z * f);
    }

    public double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    /** Unit vector, or {@link #ZERO} for a (near) zero vector. */
    public Vec normalize() {
        double l = length();
        return l < 1.0e-9 ? ZERO : scale(1.0 / l);
    }

    /** The horizontal (XZ) part, normalized; {@link #ZERO} if the vector is (near) vertical or zero. */
    public Vec horizontal() {
        return new Vec(x, 0, z).normalize();
    }

    public boolean isZero() {
        return length() < 1.0e-9;
    }
}

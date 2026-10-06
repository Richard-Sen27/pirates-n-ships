package com.richardsenger.piratesnships.combat.melee.geometry;

/** An axis-aligned box in the caller's frame (world, or relative to a ship). Min/max are sorted on construction. */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public Box {
        if (minX > maxX) { double t = minX; minX = maxX; maxX = t; }
        if (minY > maxY) { double t = minY; minY = maxY; maxY = t; }
        if (minZ > maxZ) { double t = minZ; minZ = maxZ; maxZ = t; }
    }

    /** A box of the given width (X and Z) and height standing on {@code feet}, like a mob's bounding box. */
    public static Box standing(Vec feet, double width, double height) {
        double h = width / 2;
        return new Box(feet.x() - h, feet.y(), feet.z() - h, feet.x() + h, feet.y() + height, feet.z() + h);
    }

    public Box inflate(double d) {
        return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
    }

    public Vec center() {
        return new Vec((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    public Vec closestPoint(Vec p) {
        return new Vec(clamp(p.x(), minX, maxX), clamp(p.y(), minY, maxY), clamp(p.z(), minZ, maxZ));
    }

    public boolean contains(Vec p) {
        return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}

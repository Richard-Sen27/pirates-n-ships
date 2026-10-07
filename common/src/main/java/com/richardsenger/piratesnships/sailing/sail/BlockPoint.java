package com.richardsenger.piratesnships.sailing.sail;

/** A block position for the pure sail rules (no Minecraft classes, so JUnit needs no bootstrap). */
public record BlockPoint(int x, int y, int z) {

    /** Squared distance between the two block centers [blocks²]. */
    public long distanceSquared(BlockPoint o) {
        long dx = o.x - x;
        long dy = o.y - y;
        long dz = o.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public BlockPoint below(int n) {
        return new BlockPoint(x, y - n, z);
    }
}

package com.richardsenger.piratesnships.sailing.anchor;

import net.minecraft.world.phys.Vec3;

/**
 * Pure motion of the anchor as a body of its own (docs/design.md §5.2, AN2a), one game tick at a time. The anchor's
 * position is its crown (the bottom of the model); the chain is shackled to its ring, {@link AnchorTravel#HEIGHT} above.
 *
 * <ul>
 *   <li><b>Fall</b> ({@link #fall}): in air it falls under {@link #GRAVITY}; in water its vertical speed relaxes to
 *       {@code −sinkSpeed} at {@link #SINK_RELAX} per second and its horizontal speed (kept from the ship at the drop)
 *       decays at {@code waterDrag} per second. It comes to rest on the first solid block it falls onto; a solid block
 *       in its horizontal way stops that component. The chain pays out with the distance from the hawse to the ring,
 *       up to {@code maxChain}; there the anchor hangs at the chain's end and loses its outward speed.</li>
 *   <li><b>Drag</b> ({@link #drag}): a resting anchor moves along the seabed toward the hawse (horizontally), climbs a
 *       one-block step, and stops at a wall.</li>
 *   <li><b>Heave</b> ({@link #heave}): with the chain shorter than the distance, a resting anchor slides along the
 *       seabed toward the hawse until the chain reaches it, or breaks out and rises straight along the chain when the
 *       hawse is nearly above it; a free anchor is pulled along the chain.</li>
 * </ul>
 */
public final class AnchorMotion {

    /** Fall acceleration in air [blocks/s²]. */
    public static final double GRAVITY = 20.0;
    /** Fastest fall in air [blocks/s]. */
    public static final double MAX_FALL = 40.0;
    /** Rate at which the vertical speed in water approaches the sink speed [1/s]. */
    public static final double SINK_RELAX = 5.0;
    /** Below this horizontal distance to the hawse the heaved anchor breaks out and rises [blocks]. */
    public static final double BREAK_OUT = 0.5;

    private static final double EPS = 1.0e-7;

    /** The world around the anchor: block cells. */
    public interface Terrain {
        /** Whether the block at the cell stops the anchor. */
        boolean solid(int x, int y, int z);

        /** Whether the cell holds water. */
        boolean water(int x, int y, int z);
    }

    /**
     * The anchor's body.
     *
     * @param pos        crown position, world [blocks]
     * @param vel        velocity, world [blocks/s]
     * @param paidOut    chain paid out from the hawse to the ring [blocks]
     * @param resting    whether it lies on the ground
     * @param atChainEnd whether the chain held it back this tick (it hangs at the chain's end)
     */
    public record Body(Vec3 pos, Vec3 vel, double paidOut, boolean resting, boolean atChainEnd) {

        public Vec3 ring() {
            return AnchorMotion.ring(pos);
        }

        public Body withPaidOut(double l) {
            return new Body(pos, vel, l, resting, atChainEnd);
        }
    }

    private AnchorMotion() {
    }

    /** The ring (chain end) of an anchor whose crown is at {@code crown}. */
    public static Vec3 ring(Vec3 crown) {
        return new Vec3(crown.x, crown.y + AnchorTravel.HEIGHT, crown.z);
    }

    /** The crown of an anchor whose ring is at {@code ring}. */
    public static Vec3 crown(Vec3 ring) {
        return new Vec3(ring.x, ring.y - AnchorTravel.HEIGHT, ring.z);
    }

    /** Whether the anchor's crown lies in water (tested at the middle of the shank). */
    public static boolean inWater(Vec3 crown, Terrain t) {
        return t.water(floor(crown.x), floor(crown.y + AnchorTravel.HEIGHT * 0.5), floor(crown.z));
    }

    /** Whether a crown at {@code pos} stands on a solid block. */
    public static boolean supported(Vec3 pos, Terrain t) {
        int below = (int) Math.floor(pos.y + EPS) - 1;
        return pos.y - (below + 1) < 0.05 && t.solid(floor(pos.x), below, floor(pos.z));
    }

    /**
     * One tick of free motion.
     *
     * @param hawse    hawse, world
     * @param dt       tick length [s]
     * @param sinkSpeed terminal sinking speed in water [blocks/s]
     * @param waterDrag horizontal speed decay in water [1/s]
     * @param maxChain longest the chain may get [blocks]
     */
    public static Body fall(Body b, Vec3 hawse, double dt, double sinkSpeed, double waterDrag, double maxChain, Terrain t) {
        double x = b.pos().x, y = b.pos().y, z = b.pos().z;
        double vx = b.vel().x, vy = b.vel().y, vz = b.vel().z;
        if (inWater(b.pos(), t)) {
            double e = Math.exp(-waterDrag * dt);
            vx *= e;
            vz *= e;
            vy = -sinkSpeed + (vy + sinkSpeed) * Math.exp(-SINK_RELAX * dt);
        } else {
            vy = Math.max(vy - GRAVITY * dt, -MAX_FALL);
        }
        // vertical: land on the first solid block crossed
        double ny = y + vy * dt;
        boolean landed = false;
        if (vy <= 0.0) {
            int fx = floor(x), fz = floor(z);
            for (int cy = (int) Math.floor(y + EPS) - 1; cy + 1 >= ny - EPS; cy--) {
                if (t.solid(fx, cy, fz)) {
                    ny = cy + 1;
                    landed = true;
                    break;
                }
            }
        } else if (t.solid(floor(x), floor(ny + AnchorTravel.HEIGHT), floor(z))) {
            ny = y;
        }
        if (landed) {
            vy = 0.0;
        }
        // horizontal, per axis: a solid block in the way stops that component
        int cell = floor(ny + 0.1);
        double nx = x + vx * dt;
        if (floor(nx) != floor(x) && t.solid(floor(nx), cell, floor(z))) {
            nx = x;
            vx = 0.0;
        }
        double nz = z + vz * dt;
        if (floor(nz) != floor(z) && t.solid(floor(nx), cell, floor(nz))) {
            nz = z;
            vz = 0.0;
        }
        if (landed) {
            vx = 0.0;
            vz = 0.0;
        }
        // the chain: it pays out up to maxChain, then holds the ring on a sphere around the hawse
        Vec3 ring = new Vec3(nx, ny + AnchorTravel.HEIGHT, nz);
        Vec3 d = ring.subtract(hawse);
        double dist = d.length();
        boolean atEnd = false;
        if (dist > maxChain && dist > EPS) {
            Vec3 u = d.scale(1.0 / dist);
            ring = hawse.add(u.scale(maxChain));
            double out = vx * u.x + vy * u.y + vz * u.z;
            if (out > 0.0) {
                vx -= u.x * out;
                vy -= u.y * out;
                vz -= u.z * out;
            }
            dist = maxChain;
            atEnd = true;
            landed = landed && supported(crown(ring), t);
        }
        Vec3 pos = crown(ring);
        double paidOut = Math.min(maxChain, Math.max(b.paidOut(), dist));
        return new Body(pos, landed ? Vec3.ZERO : new Vec3(vx, vy, vz), paidOut, landed, atEnd);
    }

    /**
     * Drags a resting anchor up to {@code distance} blocks horizontally toward the hawse. It climbs one block onto a
     * step and stops at a higher wall; it stays put when the hawse is straight above. The result rests if it still
     * stands on solid ground (else it falls on the next tick).
     */
    public static Body drag(Body b, Vec3 hawse, double distance, Terrain t) {
        double hx = hawse.x - b.pos().x, hz = hawse.z - b.pos().z;
        double h = Math.sqrt(hx * hx + hz * hz);
        if (distance <= 0.0 || h < EPS) {
            return b;
        }
        double step = Math.min(distance, h);
        return slide(b, b.pos().x + hx / h * step, b.pos().z + hz / h * step, t);
    }

    /**
     * Winds the chain in to {@code paidOut}: a resting anchor slides along the seabed until the chain reaches it, or
     * breaks out once the hawse is within {@link #BREAK_OUT} blocks horizontally (or the chain can no longer reach it
     * along the seabed) and is pulled straight along the chain; a free anchor sinks and is held at the chain's end.
     */
    public static Body heave(Body b, Vec3 hawse, double paidOut, double dt, double sinkSpeed, double waterDrag, Terrain t) {
        double l = Math.max(0.0, paidOut);
        if (b.resting()) {
            Vec3 ring = b.ring();
            double dy = ring.y - hawse.y;
            double hx = ring.x - hawse.x, hz = ring.z - hawse.z;
            double h = Math.sqrt(hx * hx + hz * hz);
            if (Math.sqrt(h * h + dy * dy) <= l) {
                return b.withPaidOut(l); // the chain still reaches it where it lies
            }
            double reach = l * l - dy * dy;
            if (reach > BREAK_OUT * BREAK_OUT && h > BREAK_OUT) {
                double want = Math.sqrt(reach);
                Body slid = drag(b, hawse, h - want, t).withPaidOut(l);
                if (!slid.pos().equals(b.pos())) {
                    return slid;
                }
            }
            // breaks out: straight along the chain
            return pullAlong(b, hawse, l);
        }
        Body fell = fall(b, hawse, dt, sinkSpeed, waterDrag, l, t);
        return fell.withPaidOut(l);
    }

    private static Body pullAlong(Body b, Vec3 hawse, double l) {
        Vec3 d = b.ring().subtract(hawse);
        double dist = d.length();
        Vec3 ring = dist > EPS ? hawse.add(d.scale(Math.min(1.0, l / dist))) : hawse;
        return new Body(crown(ring), Vec3.ZERO, l, false, true);
    }

    private static Body slide(Body b, double nx, double nz, Terrain t) {
        double y = b.pos().y;
        int cell = (int) Math.floor(y + EPS);
        if (t.solid(floor(nx), cell, floor(nz))) {
            if (t.solid(floor(nx), cell + 1, floor(nz)) || t.solid(floor(nx), cell + 2, floor(nz))) {
                return new Body(b.pos(), Vec3.ZERO, b.paidOut(), b.resting(), false); // a wall: caught
            }
            y = cell + 1;
        }
        Vec3 pos = new Vec3(nx, y, nz);
        return new Body(pos, Vec3.ZERO, b.paidOut(), supported(pos, t), false);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}

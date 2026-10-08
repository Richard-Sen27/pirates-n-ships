package com.richardsenger.piratesnships.worldsim.lane;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A sea lane between two ports (WS2): the waypoints in block coordinates from {@code from} to {@code to}, the first
 * and last at the ports' sea endpoints, and the length in blocks along them. {@code computedTick} is the overworld
 * game time it was found. Immutable.
 */
public record Lane(ResourceLocation from, ResourceLocation to, List<Point> waypoints, double length, long computedTick) {

    /** A waypoint (block x, z at sea level). */
    public record Point(int x, int z) {
        public static final Codec<Point> CODEC = Codec.INT.listOf().comapFlatMap(
                l -> l.size() == 2 ? com.mojang.serialization.DataResult.success(new Point(l.get(0), l.get(1)))
                        : com.mojang.serialization.DataResult.error(() -> "A lane point needs [x, z]: " + l),
                p -> List.of(p.x, p.z));

        public double distanceTo(Point o) {
            double dx = o.x - x;
            double dz = o.z - z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** The block centre of this point at height {@code y}. */
        public Vec3 at(double y) {
            return new Vec3(x + 0.5, y, z + 0.5);
        }
    }

    /**
     * A position along waypoints: block coordinates {@code x, z}, the index of the leg it is on (leg {@code i} runs from
     * waypoint {@code i} to {@code i+1}), and the compass heading of that leg in degrees (0 = north/−z, 90 = east/+x).
     */
    public record Position(double x, double z, int leg, double headingDegrees) {
        public Vec3 at(double y) {
            return new Vec3(x, y, z);
        }
    }

    public static final Codec<Lane> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("from").forGetter(Lane::from),
            ResourceLocation.CODEC.fieldOf("to").forGetter(Lane::to),
            Point.CODEC.listOf().fieldOf("waypoints").forGetter(Lane::waypoints),
            Codec.DOUBLE.fieldOf("length").forGetter(Lane::length),
            Codec.LONG.optionalFieldOf("computed_tick", 0L).forGetter(Lane::computedTick)
    ).apply(i, Lane::new));

    public Lane {
        waypoints = List.copyOf(waypoints);
    }

    /** A lane over {@code waypoints} with the length computed from them. */
    public static Lane of(ResourceLocation from, ResourceLocation to, List<Point> waypoints, long computedTick) {
        return new Lane(from, to, waypoints, length(waypoints), computedTick);
    }

    /** The same lane sailed the other way. */
    public Lane reversed() {
        List<Point> r = new ArrayList<>(waypoints);
        java.util.Collections.reverse(r);
        return new Lane(to, from, r, length, computedTick);
    }

    /** Total length of the polyline through {@code points}. */
    public static double length(List<Point> points) {
        double sum = 0;
        for (int i = 1; i < points.size(); i++) sum += points.get(i - 1).distanceTo(points.get(i));
        return sum;
    }

    /** The position {@code progress} blocks along this lane (clamped to its ends). */
    public Position positionAlong(double progress) {
        return positionAlong(waypoints, progress);
    }

    /**
     * The position {@code progress} blocks along the polyline {@code points}, clamped to its ends. A single point
     * gives that point with heading 0; an empty list gives the origin.
     */
    public static Position positionAlong(List<Point> points, double progress) {
        if (points.isEmpty()) return new Position(0, 0, 0, 0);
        if (points.size() == 1) return new Position(points.get(0).x, points.get(0).z, 0, 0);
        double left = Math.max(0.0, progress);
        for (int i = 0; i < points.size() - 1; i++) {
            Point a = points.get(i);
            Point b = points.get(i + 1);
            double seg = a.distanceTo(b);
            boolean last = i == points.size() - 2;
            if (left <= seg || last) {
                double t = seg <= 0 ? 1.0 : Math.min(1.0, left / seg);
                return new Position(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t, i, heading(a, b));
            }
            left -= seg;
        }
        throw new IllegalStateException("unreachable");
    }

    /** Compass heading from {@code a} to {@code b} in [0, 360): 0 = north (−z), 90 = east (+x). */
    public static double heading(Point a, Point b) {
        double deg = Math.toDegrees(Math.atan2(b.x - a.x, -(b.z - a.z)));
        return deg < 0 ? deg + 360.0 : deg;
    }
}

package com.richardsenger.piratesnships.worldsim.navy;

import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.lane.SeaGrid;
import com.richardsenger.piratesnships.worldsim.materialize.RouteMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pure geometry and choices of navy patrols (WS4b): where a patrol sails, the straight pursuit course capped to
 * open sea, the ring a patrol ship circles its quarry on, and the way back to the route afterwards. No world access.
 */
public final class PatrolRoutes {

    /** A port as the patrol planner sees it: id, dimension and centre. */
    public record Site(ResourceLocation id, String dimension, int x, int z) {
        double distanceTo(Site o) {
            return Math.hypot(o.x - x, o.z - z);
        }
    }

    /**
     * A planned patrol: from {@code outpost} along the lane to {@code other}, either all the way (another outpost) or
     * out {@code patrol_radius} and back ({@code outAndBack}, toward a pirate island).
     */
    public record Plan(ResourceLocation outpost, ResourceLocation other, boolean outAndBack) {
    }

    private PatrolRoutes() {
    }

    /**
     * A random patrol: a random outpost sails to a random other outpost of its dimension within {@code maxDistance};
     * an outpost without one sails out and back toward the nearest pirate island of its dimension. Empty without
     * outposts, or when the chosen one has neither.
     */
    public static Optional<Plan> choose(List<Site> outposts, List<Site> islands, double maxDistance, RandomSource rng) {
        if (outposts.isEmpty()) return Optional.empty();
        List<Site> sorted = new ArrayList<>(outposts);
        sorted.sort(Comparator.comparing(s -> s.id().toString()));
        Site origin = sorted.get(rng.nextInt(sorted.size()));
        List<Site> others = sorted.stream()
                .filter(s -> !s.id().equals(origin.id()) && s.dimension().equals(origin.dimension()) && origin.distanceTo(s) <= maxDistance)
                .toList();
        if (!others.isEmpty()) return Optional.of(new Plan(origin.id(), others.get(rng.nextInt(others.size())).id(), false));
        return islands.stream().filter(s -> s.dimension().equals(origin.dimension()))
                .min(Comparator.comparingDouble(origin::distanceTo).thenComparing(s -> s.id().toString()))
                .map(s -> new Plan(origin.id(), s.id(), true));
    }

    /**
     * Out {@code radius} blocks along {@code lane} (or its whole length if shorter) and back the same way: the
     * waypoints up to the turning point, then the same ones in reverse order to the start.
     */
    public static List<Lane.Point> outAndBack(List<Lane.Point> lane, double radius) {
        if (lane.size() < 2) return List.copyOf(lane);
        double turn = Math.min(Math.max(0.0, radius), Lane.length(lane));
        Lane.Position at = Lane.positionAlong(lane, turn);
        List<Lane.Point> out = new ArrayList<>(lane.subList(0, at.leg() + 1));
        Lane.Point tip = new Lane.Point((int) Math.round(at.x()), (int) Math.round(at.z()));
        if (!tip.equals(out.get(out.size() - 1))) out.add(tip);
        List<Lane.Point> route = new ArrayList<>(out);
        for (int i = out.size() - 2; i >= 0; i--) route.add(out.get(i));
        return route;
    }

    /**
     * The point {@code standoff} blocks short of the target on the line from the patrol at {@code (px, pz)}; the
     * patrol's own position when it is closer than that already.
     */
    public static double[] standoffPoint(double px, double pz, double tx, double tz, double standoff) {
        double d = Math.hypot(px - tx, pz - tz);
        if (d <= standoff || d < 1e-9) return new double[]{px, pz};
        double k = standoff / d;
        return new double[]{tx + (px - tx) * k, tz + (pz - tz) * k};
    }

    /**
     * How far a patrol at {@code (fx, fz)} may sail straight toward {@code (tx, tz)} over open sea: walking the line in
     * half-cell steps, the first cell that is not sea after the line has reached the sea stops it, and the last point
     * before is returned. Land cells at the very start (a patrol leaving a coast or a harbour) do not stop it.
     */
    public static Lane.Point capToSea(SeaGrid grid, double fx, double fz, double tx, double tz) {
        double d = Math.hypot(tx - fx, tz - fz);
        double step = Math.max(1.0, grid.cellBlocks() / 2.0);
        int n = (int) Math.ceil(d / step);
        boolean seenSea = isSea(grid, fx, fz);
        double lx = fx, lz = fz;
        for (int i = 1; i <= n; i++) {
            double t = Math.min(1.0, i * step / d);
            double x = fx + (tx - fx) * t, z = fz + (tz - fz) * t;
            if (isSea(grid, x, z)) {
                seenSea = true;
            } else if (seenSea) {
                break;
            }
            lx = x;
            lz = z;
        }
        return new Lane.Point((int) Math.round(lx), (int) Math.round(lz));
    }

    private static boolean isSea(SeaGrid grid, double x, double z) {
        return grid.isSea(grid.cellOf((int) Math.floor(x)), grid.cellOf((int) Math.floor(z)));
    }

    /**
     * The abstract pursuit course of a patrol at {@code (px, pz)}: straight toward the point {@code standoff} short of
     * the target at {@code (tx, tz)}, capped to open sea.
     */
    public static List<Lane.Point> pursuit(SeaGrid grid, double px, double pz, double tx, double tz, double standoff) {
        double[] aim = standoffPoint(px, pz, tx, tz, standoff);
        Lane.Point start = new Lane.Point((int) Math.round(px), (int) Math.round(pz));
        return List.of(start, capToSea(grid, px, pz, aim[0], aim[1]));
    }

    /**
     * {@code points} waypoints on a ring of {@code radius} around the target at {@code (tx, tz)}, at height {@code y},
     * starting with the one nearest the patrol at {@code (px, pz)} and going round counter-clockwise (seen from above:
     * from east toward north). A looping course over them keeps the patrol circling its quarry.
     */
    public static List<Vec3> ring(double tx, double tz, double radius, double px, double pz, int points, double y) {
        double start = Math.atan2(pz - tz, px - tx);
        List<Vec3> out = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double a = start - i * 2.0 * Math.PI / points;
            out.add(new Vec3(tx + Math.cos(a) * radius, y, tz + Math.sin(a) * radius));
        }
        return out;
    }

    /**
     * The way back to the route {@code home} for a patrol at {@code (x, z)} that left it at {@code homeProgress}: to the
     * route point nearest to it (nearest to {@code homeProgress} among equals), then the rest of the route.
     */
    public static List<Lane.Point> rejoin(List<Lane.Point> home, double homeProgress, double x, double z) {
        Lane.Point here = new Lane.Point((int) Math.round(x), (int) Math.round(z));
        if (home.size() < 2) {
            return home.isEmpty() ? List.of(here, here) : List.of(here, home.get(home.size() - 1));
        }
        double p = RouteMath.project(home, x, z, homeProgress);
        Lane.Position on = Lane.positionAlong(home, p);
        List<Lane.Point> route = new ArrayList<>();
        route.add(here);
        add(route, new Lane.Point((int) Math.round(on.x()), (int) Math.round(on.z())));
        for (Lane.Point w : RouteMath.ahead(home, p)) add(route, w);
        if (route.size() < 2) route.add(here);
        return route;
    }

    private static void add(List<Lane.Point> route, Lane.Point p) {
        if (!route.get(route.size() - 1).equals(p)) route.add(p);
    }
}

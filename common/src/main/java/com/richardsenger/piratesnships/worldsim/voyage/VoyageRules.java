package com.richardsenger.piratesnships.worldsim.voyage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The pure rules of abstract voyages (WS2): movement with the wind, spawn chances, and the choice of a convoy's ports
 * and cargo. No world access.
 */
public final class VoyageRules {

    /** Ticks per in-game day. */
    public static final int TICKS_PER_DAY = 24_000;
    /** Most different goods a convoy carries. */
    public static final int MAX_CARGO_GOODS = 3;

    /** Speed tuning: blocks per second and the wind factors (straight astern / straight ahead). */
    public record Speed(double blocksPerSecond, boolean windAffects, double downwind, double headwind) {
    }

    /**
     * What a port offers convoys: its id, dimension, centre (x, z), the goods it PRODUCES and DEMANDS, and every good
     * it trades.
     */
    public record PortView(ResourceLocation id, String dimension, int x, int z, List<ResourceLocation> produces, List<ResourceLocation> demands,
                           Set<ResourceLocation> traded) {
        public double distanceTo(PortView o) {
            double dx = o.x - x, dz = o.z - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /** A planned convoy: from, to, and units per good. */
    public record ConvoyPlan(ResourceLocation from, ResourceLocation to, Map<ResourceLocation, Integer> cargo) {
    }

    private VoyageRules() {
    }

    // --- Movement -------------------------------------------------------------------------------------------

    /**
     * The speed factor of a ship heading {@code headingDegrees} in a wind blowing toward {@code windTowardDegrees}
     * (compass degrees): {@code headwind} straight into the wind, {@code downwind} straight before it, linear in
     * {@code (1 + cos angle) / 2} between. 1 when the wind does not affect speed or there is no wind.
     */
    public static double windFactor(double headingDegrees, double windTowardDegrees, double windStrength, Speed s) {
        if (!s.windAffects() || windStrength <= 0) return 1.0;
        double cos = Math.cos(Math.toRadians(headingDegrees - windTowardDegrees));
        return s.headwind() + (s.downwind() - s.headwind()) * (1.0 + cos) / 2.0;
    }

    /** Blocks sailed in {@code ticks} at {@code windFactor}. */
    public static double blocksIn(Speed s, int ticks, double windFactor) {
        return Math.max(0.0, s.blocksPerSecond() * ticks / 20.0 * windFactor);
    }

    /** {@code voyage} moved {@code blocks} further along its route (clamped at the end). */
    public static Voyage advance(Voyage voyage, double blocks) {
        return voyage.withProgress(voyage.progress() + Math.max(0.0, blocks));
    }

    // --- Spawning -------------------------------------------------------------------------------------------

    /** Chance per check of {@code intervalTicks} for {@code perDay} expected departures per day, capped at 1. */
    public static double chancePerCheck(double perDay, int intervalTicks) {
        if (perDay <= 0 || intervalTicks <= 0) return 0.0;
        return Math.min(1.0, perDay * intervalTicks / TICKS_PER_DAY);
    }

    /** Whether a spawn roll succeeds: {@code roll < chance} and the cap is not reached. */
    public static boolean rollSpawn(double chance, double roll, int active, int cap) {
        return active < cap && roll < chance;
    }

    // --- Convoys --------------------------------------------------------------------------------------------

    /**
     * A random convoy among {@code ports}: the origin weighted by its number of PRODUCES goods (only origins with a
     * destination), the destination among the ports between {@code minDistance} and {@code maxDistance} that DEMAND
     * one of its goods, weighted by 1/distance, and the cargo from {@link #cargo}. Empty if no pair fits.
     */
    public static Optional<ConvoyPlan> planConvoy(List<PortView> ports, double minDistance, double maxDistance, int units,
                                                  RandomSource rng) {
        List<PortView> sorted = new ArrayList<>(ports);
        sorted.sort(Comparator.comparing(p -> p.id().toString()));
        List<PortView> origins = new ArrayList<>();
        List<List<PortView>> destinations = new ArrayList<>();
        for (PortView o : sorted) {
            if (o.produces().isEmpty()) continue;
            List<PortView> d = destinationsOf(o, sorted, minDistance, maxDistance);
            if (d.isEmpty()) continue;
            origins.add(o);
            destinations.add(d);
        }
        if (origins.isEmpty()) return Optional.empty();
        double[] ow = new double[origins.size()];
        for (int i = 0; i < ow.length; i++) ow[i] = origins.get(i).produces().size();
        int oi = pick(ow, rng);
        PortView origin = origins.get(oi);
        List<PortView> dests = destinations.get(oi);
        double[] dw = new double[dests.size()];
        for (int i = 0; i < dw.length; i++) dw[i] = 1.0 / Math.max(1.0, origin.distanceTo(dests.get(i)));
        PortView dest = dests.get(pick(dw, rng));
        return Optional.of(new ConvoyPlan(origin.id(), dest.id(), cargo(origin, dest, units, rng)));
    }

    /** Ports of the same dimension in range that DEMAND at least one good {@code origin} PRODUCES. */
    public static List<PortView> destinationsOf(PortView origin, List<PortView> ports, double minDistance, double maxDistance) {
        List<PortView> out = new ArrayList<>();
        for (PortView p : ports) {
            if (p.id().equals(origin.id()) || !p.dimension().equals(origin.dimension())) continue;
            double d = origin.distanceTo(p);
            if (d < minDistance || d > maxDistance) continue;
            if (origin.produces().stream().anyMatch(p.demands()::contains)) out.add(p);
        }
        return out;
    }

    /**
     * The cargo of a convoy from {@code origin} to {@code dest}: 1 to {@value #MAX_CARGO_GOODS} goods ({@code units}
     * each) that the origin PRODUCES and the destination trades, the ones it DEMANDS first. Without such goods, goods
     * both ports trade; empty if they share none.
     */
    public static Map<ResourceLocation, Integer> cargo(PortView origin, PortView dest, int units, RandomSource rng) {
        List<ResourceLocation> demanded = new ArrayList<>();
        List<ResourceLocation> others = new ArrayList<>();
        for (ResourceLocation g : origin.produces()) {
            if (dest.demands().contains(g)) demanded.add(g);
            else if (dest.traded().contains(g)) others.add(g);
        }
        if (demanded.isEmpty() && others.isEmpty()) {
            origin.traded().stream().filter(dest.traded()::contains).sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(others::add);
        }
        shuffle(demanded, rng);
        shuffle(others, rng);
        List<ResourceLocation> pool = new ArrayList<>(demanded);
        pool.addAll(others);
        int count = Math.min(pool.size(), 1 + rng.nextInt(MAX_CARGO_GOODS));
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) out.put(pool.get(i), units);
        return out;
    }

    /** An index picked with probability proportional to {@code weights} (all ≥ 0, at least one > 0). */
    static int pick(double[] weights, RandomSource rng) {
        double total = 0;
        for (double w : weights) total += Math.max(0, w);
        double r = rng.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r < 0) return i;
        }
        return weights.length - 1;
    }

    private static <T> void shuffle(List<T> list, RandomSource rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }
}

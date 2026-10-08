package com.richardsenger.piratesnships.worldsim.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.law.flag.Faction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The faction state of a world (design.md §10.4, WS1): per faction an aggression in 0..1 and a wealth (a non-negative
 * long in abstract doubloons), per pair of factions a tension in 0..1. Immutable; {@link FactionRules} makes new
 * states. Every faction and pair always has a value: missing entries (an older save) read as the defaults, and values
 * out of range are clamped.
 */
public record FactionState(Map<Faction, Double> aggression, Map<Faction, Long> wealth, Map<FactionPair, Double> tension) {

    /** The aggression every faction decays toward each day. */
    public static final double REST_AGGRESSION = 0.3;
    /** The tension every pair decays toward each day. */
    public static final double REST_TENSION = 0.0;

    public static final Codec<Faction> FACTION_CODEC = Codec.STRING.xmap(
            s -> Faction.valueOf(s.toUpperCase(Locale.ROOT)), f -> f.name().toLowerCase(Locale.ROOT));

    public static final Codec<FactionState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(FACTION_CODEC, Codec.DOUBLE).optionalFieldOf("aggression", Map.of()).forGetter(FactionState::aggression),
            Codec.unboundedMap(FACTION_CODEC, Codec.LONG).optionalFieldOf("wealth", Map.of()).forGetter(FactionState::wealth),
            Codec.unboundedMap(FactionPair.CODEC, Codec.DOUBLE).optionalFieldOf("tension", Map.of()).forGetter(FactionState::tension)
    ).apply(i, FactionState::new));

    /** A new world: everyone at rest aggression, a little bad blood between navy and pirates, the default purses. */
    public static final FactionState INITIAL = new FactionState(Map.of(), Map.of(), Map.of());

    public FactionState {
        EnumMap<Faction, Double> ag = new EnumMap<>(Faction.class);
        EnumMap<Faction, Long> we = new EnumMap<>(Faction.class);
        EnumMap<FactionPair, Double> te = new EnumMap<>(FactionPair.class);
        for (Faction f : Faction.values()) {
            ag.put(f, clamp01(aggression.getOrDefault(f, REST_AGGRESSION)));
            we.put(f, Math.max(0L, wealth.getOrDefault(f, defaultWealth(f))));
        }
        for (FactionPair p : FactionPair.values()) {
            te.put(p, clamp01(tension.getOrDefault(p, defaultTension(p))));
        }
        aggression = Collections.unmodifiableMap(ag);
        wealth = Collections.unmodifiableMap(we);
        tension = Collections.unmodifiableMap(te);
    }

    /** The wealth a faction starts with. */
    public static long defaultWealth(Faction f) {
        return switch (f) {
            case NAVY -> 10_000L;
            case PIRATES -> 2_000L;
            case MERCHANTS -> 10_000L;
        };
    }

    /** The tension a pair starts with. */
    public static double defaultTension(FactionPair p) {
        return p == FactionPair.NAVY_PIRATES ? 0.2 : 0.0;
    }

    public double aggression(Faction f) {
        return aggression.get(f);
    }

    public long wealth(Faction f) {
        return wealth.get(f);
    }

    /** Tension between {@code a} and {@code b} in any order; 0 for a faction with itself. */
    public double tension(Faction a, Faction b) {
        return a == b ? 0.0 : tension.get(FactionPair.of(a, b));
    }

    public double tension(FactionPair p) {
        return tension.get(p);
    }

    /** A copy with one aggression replaced (clamped). */
    public FactionState withAggression(Faction f, double value) {
        EnumMap<Faction, Double> ag = new EnumMap<>(aggression);
        ag.put(f, value);
        return new FactionState(ag, wealth, tension);
    }

    /** A copy with one wealth replaced (at least 0). */
    public FactionState withWealth(Faction f, long value) {
        EnumMap<Faction, Long> we = new EnumMap<>(wealth);
        we.put(f, value);
        return new FactionState(aggression, we, tension);
    }

    /** A copy with one tension replaced (clamped). */
    public FactionState withTension(FactionPair p, double value) {
        EnumMap<FactionPair, Double> te = new EnumMap<>(tension);
        te.put(p, value);
        return new FactionState(aggression, wealth, te);
    }

    static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}

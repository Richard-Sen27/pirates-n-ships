package com.richardsenger.piratesnships.worldsim.faction;

import com.richardsenger.piratesnships.law.flag.Faction;

import java.util.EnumMap;

/**
 * The pure rules of the faction state (design.md §10.4, WS1): applying an event and the daily decay. No world access.
 */
public final class FactionRules {

    private FactionRules() {
    }

    /**
     * {@code state} after {@code event} at {@code strength} (each shift × strength; aggression and tension clamped to
     * 0..1, wealth rounded and clamped to 0..{@code maxWealth}).
     */
    public static FactionState apply(FactionState state, FactionEvent event, double strength, long maxWealth) {
        if (strength <= 0.0 || !Double.isFinite(strength)) return state;
        EnumMap<Faction, Double> ag = new EnumMap<>(state.aggression());
        EnumMap<Faction, Long> we = new EnumMap<>(state.wealth());
        EnumMap<FactionPair, Double> te = new EnumMap<>(state.tension());
        for (FactionEvent.Shift s : event.shifts()) {
            double d = s.amount() * strength;
            switch (s.kind()) {
                case AGGRESSION -> ag.merge(s.faction(), d, Double::sum);
                case TENSION -> te.merge(s.pair(), d, Double::sum);
                case WEALTH -> we.put(s.faction(), clampWealth(we.get(s.faction()) + Math.round(d), maxWealth));
            }
        }
        return new FactionState(ag, we, te);
    }

    /**
     * {@code state} after {@code days} days of decay: every aggression moves {@code perDay × days} toward
     * {@link FactionState#REST_AGGRESSION}, every tension toward {@link FactionState#REST_TENSION}, without
     * overshooting. Wealth does not decay.
     */
    public static FactionState decay(FactionState state, double perDay, long days) {
        if (days <= 0 || perDay <= 0.0) return state;
        double step = Math.min(1.0, perDay * days);
        EnumMap<Faction, Double> ag = new EnumMap<>(Faction.class);
        EnumMap<FactionPair, Double> te = new EnumMap<>(FactionPair.class);
        state.aggression().forEach((f, v) -> ag.put(f, toward(v, FactionState.REST_AGGRESSION, step)));
        state.tension().forEach((p, v) -> te.put(p, toward(v, FactionState.REST_TENSION, step)));
        return new FactionState(ag, state.wealth(), te);
    }

    /** {@code value} moved by at most {@code step} toward {@code target}. */
    static double toward(double value, double target, double step) {
        if (value > target) return Math.max(target, value - step);
        if (value < target) return Math.min(target, value + step);
        return value;
    }

    static long clampWealth(long value, long max) {
        return Math.max(0L, Math.min(Math.max(0L, max), value));
    }
}

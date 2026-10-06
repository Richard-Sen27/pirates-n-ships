package com.richardsenger.piratesnships.crew.provisions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The provision rules (design.md §7.4): consumption, shortages, rum, scurvy and spoilage for one ship. Pure and
 * deterministic: no world, no config, no randomness.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li><b>Demand</b> flows continuously: per tick the crew needs {@code eaters × perCrewPerDay × rate / 24000} of
 *       nutrition and of water rations, where {@code eaters = crew + prisoners × prisonerShare}. Rum demand is
 *       {@code crew × rumRation × rumRations × rate / 24000} (prisoners get no rum).</li>
 *   <li><b>Units are taken whole, lazily</b>: a unit leaves the store at the moment the demand exceeds what was
 *       already taken; the unused rest of the unit is kept as a credit in {@link ProvisioningState}. Because the
 *       number of units taken by time {@code t} depends only on the cumulative demand up to {@code t}, calling
 *       {@link #advance} every tick, every 100 ticks or once after a long gap gives the same store and state.
 *       Long gaps are split internally at every spoilage moment so that this also holds with spoilage.</li>
 *   <li><b>Nutrition mapping</b>: one crew member is fed for one day by {@link ProvisionSettings#foodPerCrewPerDay()}
 *       nutrition (vanilla food component nutrition, default 6). A bread (5) feeds one sailor for 0.83 days.</li>
 *   <li><b>Eating order</b>: perishable food closest to spoiling first, then everything that keeps (preserved food);
 *       ties go to the smaller value per unit first (use up scraps, keep dense rations), then by id, then oldest
 *       lot first. Water and rum follow the same order (they never spoil).</li>
 *   <li><b>Shortage</b>: when food (water) runs out, the crew is hungry (thirsty) until supplies come back. The
 *       continuous duration drives the desertion risk; the time spent short drives morale loss.</li>
 *   <li><b>Rum</b>: while issued, morale rises by {@code rumMoralePerDay × min(rumRations, 1)} per day. Issuing more
 *       than the ration holds the drunk timer at {@link ProvisionSettings#drunkDurationTicks()}; it counts down once
 *       the over-issue stops (or the rum runs out).</li>
 *   <li><b>Scurvy</b>: a clock counts the time since the crew last ate anti-scurvy food (tagged citrus, plus any
 *       fresh food when {@link ProvisionSettings#freshFoodPreventsScurvy()}). At the onset time the crew has
 *       scurvy; the next anti-scurvy unit eaten cures it. The clock only runs while someone is eating.</li>
 *   <li><b>Spoilage</b>: perishable lots age while spoilage is on and vanish when their shelf life is reached; the
 *       spoiled units are reported so the pantry can remove (or replace) the items.</li>
 *   <li><b>Consumption off</b>: nothing changes at all (no consumption, no aging, no penalties).</li>
 * </ul>
 */
public final class ProvisionRules {

    private static final double EPS = 1e-7;
    private static final double TPD = ProvisionSettings.TICKS_PER_DAY;

    /** Eating order within one kind. */
    static final Comparator<Work> EATING_ORDER = Comparator
            .comparing((Work w) -> !w.type.perishable())
            .thenComparingLong(Work::remainingShelfLife)
            .thenComparingDouble(w -> w.type.valuePerUnit())
            .thenComparing(w -> w.type.id())
            .thenComparing(Comparator.comparingLong((Work w) -> w.age).reversed());

    private ProvisionRules() {
    }

    /** Runs {@code ticks} of provisioning for one ship. {@code ticks <= 0} changes nothing. */
    public static ProvisionUpdate advance(ProvisionStore store, ProvisioningState state, CrewHeadcount crew,
                                          ProvisionSettings settings, long ticks) {
        if (!settings.consumptionEnabled() || ticks <= 0) {
            return new ProvisionUpdate(store, state, ProvisionOutcome.idle(Math.max(0, ticks)));
        }
        Sim sim = new Sim(store, state, crew, settings);
        sim.run(ticks);
        return sim.result(ticks);
    }

    /** Days of food, water and rum left at the current headcount, accounting for spoilage. */
    public static SuppliesLeft suppliesLeft(ProvisionStore store, ProvisioningState state, CrewHeadcount crew,
                                            ProvisionSettings settings) {
        if (!settings.consumptionEnabled()) {
            return SuppliesLeft.UNLIMITED;
        }
        Sim sim = new Sim(store, state, crew, settings);
        double horizon = 0;
        if (sim.rFood > 0) horizon = Math.max(horizon, (state.foodCredit() + store.totalValue(ProvisionKind.FOOD)) / sim.rFood);
        if (sim.rWater > 0) horizon = Math.max(horizon, (state.waterCredit() + store.totalValue(ProvisionKind.WATER)) / sim.rWater);
        if (sim.rRum > 0) horizon = Math.max(horizon, (state.rumCredit() + store.totalValue(ProvisionKind.RUM)) / sim.rRum);
        long h = (long) Math.ceil(horizon) + 2;
        sim.run(h);
        return new SuppliesLeft(days(sim.rFood, sim.foodOutAt, h), days(sim.rWater, sim.waterOutAt, h), days(sim.rRum, sim.rumOutAt, h));
    }

    private static double days(double rate, double outAt, long horizon) {
        if (rate <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (outAt >= 0 ? outAt : horizon) / TPD;
    }

    /** Whether eating this provision resets the scurvy clock. */
    public static boolean preventsScurvy(ProvisionType type, ProvisionSettings settings) {
        return type.kind() == ProvisionKind.FOOD
                && (type.antiScurvy() || (settings.freshFoodPreventsScurvy() && !type.preserved()));
    }

    /** Mutable working copy of a lot. */
    static final class Work {
        final ProvisionType type;
        int units;
        long age;

        Work(ProvisionLot lot) {
            this.type = lot.type();
            this.units = lot.units();
            this.age = lot.ageTicks();
        }

        long remainingShelfLife() {
            return type.perishable() ? Math.max(0, type.shelfLifeTicks() - age) : Long.MAX_VALUE;
        }
    }

    /** Receives the eating moments of anti-scurvy units: first at {@code time}, then {@code count - 1} more every {@code spacing}. */
    private interface EatListener {
        void eaten(double time, double spacing, int count);
    }

    /** Result of consuming one kind over one segment. */
    private record Taken(double credit, double outAt) {
        boolean ranOut() {
            return outAt >= 0;
        }
    }

    private static final class Sim {
        final ProvisionSettings s;
        final List<Work> food = new ArrayList<>(), water = new ArrayList<>(), rum = new ArrayList<>();
        final double rFood, rWater, rRum;
        final boolean overdrinking;
        final double onset;
        final double rumMoraleFactor;
        final Map<String, Integer> consumed = new TreeMap<>(), spoiled = new TreeMap<>();

        double foodCredit, waterCredit, rumCredit, hungry, thirsty, scurvyClock, drunk;
        double hungryTicks, thirstyTicks, rumTicks, scurvyTicks;
        double foodOutAt = -1, waterOutAt = -1, rumOutAt = -1;
        double elapsed;

        Sim(ProvisionStore store, ProvisioningState st, CrewHeadcount crew, ProvisionSettings s) {
            this.s = s;
            for (ProvisionLot lot : store.lots()) {
                switch (lot.type().kind()) {
                    case FOOD -> food.add(new Work(lot));
                    case WATER -> water.add(new Work(lot));
                    case RUM -> rum.add(new Work(lot));
                }
            }
            double rate = Math.max(0, s.consumptionRate());
            double eaters = crew.crew() + crew.prisoners() * Math.max(0, s.prisonerShare());
            rFood = eaters * s.foodPerCrewPerDay() * rate / TPD;
            rWater = eaters * s.waterPerCrewPerDay() * rate / TPD;
            rRum = crew.crew() * s.rumRationPerCrewPerDay() * crew.rumRations() * rate / TPD;
            overdrinking = crew.rumRations() > 1 + 1e-9;
            rumMoraleFactor = Math.min(crew.rumRations(), 1.0);
            onset = s.scurvyOnsetTicks();
            foodCredit = clean(st.foodCredit());
            waterCredit = clean(st.waterCredit());
            rumCredit = clean(st.rumCredit());
            hungry = st.hungryTicks();
            thirsty = st.thirstyTicks();
            scurvyClock = s.scurvyEnabled() ? st.ticksSinceAntiScurvy() : 0;
            drunk = st.drunkTicks();
        }

        void run(long ticks) {
            long remaining = ticks;
            spoil();
            while (remaining > 0) {
                long seg = remaining;
                if (s.spoilageEnabled()) {
                    for (Work w : food) {
                        if (w.units > 0 && w.type.perishable()) {
                            seg = Math.min(seg, w.remainingShelfLife());
                        }
                    }
                }
                segment(seg);
                if (s.spoilageEnabled()) {
                    for (List<Work> list : List.of(food, water, rum)) {
                        for (Work w : list) {
                            w.age += seg;
                        }
                    }
                }
                spoil();
                elapsed += seg;
                remaining -= seg;
            }
        }

        private void spoil() {
            if (!s.spoilageEnabled()) {
                return;
            }
            for (Work w : food) {
                if (w.units > 0 && w.type.perishable() && w.remainingShelfLife() == 0) {
                    spoiled.merge(w.type.id(), w.units, Integer::sum);
                    w.units = 0;
                }
            }
        }

        private void segment(long seg) {
            // Food (with scurvy clock)
            List<double[]> antiScurvyEats = new ArrayList<>();
            Taken f = take(food, foodCredit, rFood, seg, (t, g, n) -> antiScurvyEats.add(new double[]{t, g, n}));
            foodCredit = f.credit();
            hungry = shortage(f, rFood, seg, hungry);
            if (f.ranOut()) {
                hungryTicks += seg - f.outAt();
                if (foodOutAt < 0) foodOutAt = elapsed + f.outAt();
            }
            if (s.scurvyEnabled() && rFood > 0) {
                scurvy(antiScurvyEats, seg);
            }

            // Water
            Taken w = take(water, waterCredit, rWater, seg, null);
            waterCredit = w.credit();
            thirsty = shortage(w, rWater, seg, thirsty);
            if (w.ranOut()) {
                thirstyTicks += seg - w.outAt();
                if (waterOutAt < 0) waterOutAt = elapsed + w.outAt();
            }

            // Rum
            Taken r = take(rum, rumCredit, rRum, seg, null);
            rumCredit = r.credit();
            if (rRum > 0) {
                double rumUntil = r.ranOut() ? r.outAt() : seg;
                if (r.ranOut() && rumOutAt < 0) rumOutAt = elapsed + r.outAt();
                rumTicks += rumUntil;
                if (overdrinking && rumUntil > 0) {
                    drunk = Math.max(0, s.drunkDurationTicks() - (seg - rumUntil));
                } else {
                    drunk = Math.max(0, drunk - seg);
                }
            } else {
                drunk = Math.max(0, drunk - seg);
            }
        }

        /** New continuous shortage duration after a segment. */
        private static double shortage(Taken t, double rate, long seg, double before) {
            if (rate <= 0 || !t.ranOut()) {
                return 0;
            }
            return t.outAt() == 0 ? before + seg : seg - t.outAt();
        }

        private void scurvy(List<double[]> eats, long seg) {
            double pos = 0;
            for (double[] e : eats) {
                double t = e[0], g = e[1];
                int n = (int) e[2];
                clock(t - pos);
                scurvyClock = 0;
                scurvyTicks += (n - 1) * Math.max(0, g - onset);
                pos = t + (n - 1) * g;
            }
            clock(seg - pos);
        }

        private void clock(double dt) {
            if (dt <= 0) {
                return;
            }
            scurvyTicks += Math.max(0, scurvyClock + dt - Math.max(scurvyClock, onset));
            scurvyClock += dt;
        }

        /** Takes whole units of one kind to cover {@code rate × seg} of demand, in eating order. */
        private Taken take(List<Work> lots, double credit, double rate, long seg, EatListener listener) {
            if (rate <= 0) {
                return new Taken(credit, -1);
            }
            double demand = rate * seg;
            double avail = credit;
            if (avail + EPS < demand) {
                lots.sort(EATING_ORDER);
                for (Work w : lots) {
                    if (avail + EPS >= demand) {
                        break;
                    }
                    if (w.units == 0) {
                        continue;
                    }
                    double value = w.type.valuePerUnit();
                    int need = (int) Math.min(Integer.MAX_VALUE, Math.ceil((demand - avail) / value - 1e-9));
                    int n = Math.max(1, Math.min(need, w.units));
                    if (listener != null && preventsScurvy(w.type, s)) {
                        listener.eaten(avail / rate, value / rate, n);
                    }
                    avail += n * value;
                    w.units -= n;
                    consumed.merge(w.type.id(), n, Integer::sum);
                }
            }
            if (avail + EPS >= demand) {
                return new Taken(clean(avail - demand), -1);
            }
            return new Taken(0, avail / rate);
        }

        private static double clean(double credit) {
            return credit < EPS ? 0 : credit;
        }

        ProvisionUpdate result(long ticks) {
            List<ProvisionLot> lots = new ArrayList<>();
            for (List<Work> list : List.of(food, water, rum)) {
                for (Work w : list) {
                    if (w.units > 0) {
                        lots.add(new ProvisionLot(w.type, w.units, w.age));
                    }
                }
            }
            ProvisionStore store = ProvisionStore.of(lots);
            ProvisioningState state = new ProvisioningState(foodCredit, waterCredit, rumCredit, hungry, thirsty,
                    s.scurvyEnabled() ? scurvyClock : 0, drunk);

            boolean isHungry = hungry > 0;
            boolean isThirsty = thirsty > 0;
            boolean isDrunk = drunk > 0;
            boolean rumIssued = rRum > 0 && (rumCredit > 0 || store.totalValue(ProvisionKind.RUM) > 0);
            boolean scurvy = s.scurvyEnabled() && scurvyClock >= onset;

            double morale = -s.hungerMoralePerDay() * hungryTicks / TPD
                    - s.thirstMoralePerDay() * thirstyTicks / TPD
                    + s.rumMoralePerDay() * rumMoraleFactor * rumTicks / TPD
                    - (s.scurvyEnabled() ? s.scurvyMoralePerDay() * scurvyTicks / TPD : 0);
            double speed = (isHungry ? s.hungryWorkSpeed() : 1) * (isThirsty ? s.thirstyWorkSpeed() : 1) * (isDrunk ? s.drunkWorkSpeed() : 1);
            double risk = Math.max(risk(hungry, s.hungerDesertionDays()), risk(thirsty, s.thirstDesertionDays()));

            ProvisionEffects effects = new ProvisionEffects(morale, speed, risk, scurvy);
            ProvisionOutcome outcome = new ProvisionOutcome(ticks, isHungry, isThirsty, rumIssued, isDrunk,
                    hungryTicks, thirstyTicks, rumTicks, consumed, spoiled, effects);
            return new ProvisionUpdate(store, state, outcome);
        }

        private static double risk(double streak, double criticalDays) {
            if (streak <= 0) {
                return 0;
            }
            if (criticalDays <= 0) {
                return 1;
            }
            return Math.min(1, streak / (criticalDays * TPD));
        }
    }
}

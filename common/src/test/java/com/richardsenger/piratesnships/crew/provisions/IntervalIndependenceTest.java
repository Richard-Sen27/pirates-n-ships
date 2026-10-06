package com.richardsenger.piratesnships.crew.provisions;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.LongSupplier;

import static com.richardsenger.piratesnships.crew.provisions.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every tick, every 100 ticks, irregular steps or once after a long gap must give the same result. */
class IntervalIndependenceTest {

    private static final long TOTAL = 6 * DAY + 1234;

    /** A voyage with a bit of everything: spoilage, citrus, scurvy, running out of food, rum over-issue running out. */
    private static ProvisionStore voyage() {
        return ProvisionStore.of(
                new ProvisionLot(BEEF, 7, 3 * DAY),
                new ProvisionLot(BEEF, 5, 0),
                new ProvisionLot(APPLE, 3, DAY),
                new ProvisionLot(BREAD, 4, 0),
                new ProvisionLot(KELP, 13, 0),
                new ProvisionLot(WATER, 30, 0),
                new ProvisionLot(RUM, 3, 0));
    }

    private static final ProvisionSettings SETTINGS = S.toBuilder().scurvyOnsetDays(1.5).freshFoodPreventsScurvy(false).build();
    private static final CrewHeadcount CREW = new CrewHeadcount(3, 2, 1.5);

    private record Sum(ProvisionStore store, ProvisioningState state, double morale, double hungry, double thirsty, double rum,
                       Map<String, Integer> consumed, Map<String, Integer> spoiled, ProvisionOutcome last) {
    }

    private static Sum simulate(LongSupplier step) {
        ProvisionStore store = voyage();
        ProvisioningState state = ProvisioningState.INITIAL;
        double morale = 0, hungry = 0, thirsty = 0, rum = 0;
        Map<String, Integer> consumed = new TreeMap<>(), spoiled = new TreeMap<>();
        ProvisionOutcome last = null;
        long done = 0;
        while (done < TOTAL) {
            long dt = Math.min(step.getAsLong(), TOTAL - done);
            ProvisionUpdate u = ProvisionRules.advance(store, state, CREW, SETTINGS, dt);
            store = u.store();
            state = u.state();
            last = u.outcome();
            morale += last.effects().moraleChange();
            hungry += last.hungryTicks();
            thirsty += last.thirstyTicks();
            rum += last.rumTicks();
            last.consumed().forEach((k, v) -> consumed.merge(k, v, Integer::sum));
            last.spoiled().forEach((k, v) -> spoiled.merge(k, v, Integer::sum));
            done += dt;
        }
        return new Sum(store, state, morale, hungry, thirsty, rum, consumed, spoiled, last);
    }

    private static void assertSame(Sum expected, Sum actual) {
        assertEquals(expected.store(), actual.store());
        assertEquals(expected.consumed(), actual.consumed());
        assertEquals(expected.spoiled(), actual.spoiled());
        ProvisioningState a = expected.state(), b = actual.state();
        assertEquals(a.foodCredit(), b.foodCredit(), 1e-6);
        assertEquals(a.waterCredit(), b.waterCredit(), 1e-6);
        assertEquals(a.rumCredit(), b.rumCredit(), 1e-6);
        assertEquals(a.hungryTicks(), b.hungryTicks(), 1e-4);
        assertEquals(a.thirstyTicks(), b.thirstyTicks(), 1e-4);
        assertEquals(a.ticksSinceAntiScurvy(), b.ticksSinceAntiScurvy(), 1e-4);
        assertEquals(a.drunkTicks(), b.drunkTicks(), 1e-4);
        assertEquals(expected.morale(), actual.morale(), 1e-6);
        assertEquals(expected.hungry(), actual.hungry(), 1e-4);
        assertEquals(expected.thirsty(), actual.thirsty(), 1e-4);
        assertEquals(expected.rum(), actual.rum(), 1e-4);
        assertEquals(expected.last().hungry(), actual.last().hungry());
        assertEquals(expected.last().thirsty(), actual.last().thirsty());
        assertEquals(expected.last().drunk(), actual.last().drunk());
        assertEquals(expected.last().effects().scurvy(), actual.last().effects().scurvy());
        assertEquals(expected.last().effects().workSpeedMultiplier(), actual.last().effects().workSpeedMultiplier(), 1e-9);
        assertEquals(expected.last().effects().desertionRisk(), actual.last().effects().desertionRisk(), 1e-6);
    }

    @Test
    void scenarioExercisesEveryRule() {
        Sum once = simulate(() -> TOTAL);
        assertTrue(once.last().hungry(), "the food runs out during the voyage");
        assertFalse(once.spoiled().isEmpty(), "some beef spoils");
        assertTrue(once.consumed().containsKey("apple"), "apples are eaten");
        assertTrue(once.rum() > 0 && once.consumed().get("rum") == 3, "rum issued until it runs out");
        assertTrue(once.morale() < 0);
    }

    @Test
    void everyTickEqualsOnce() {
        assertSame(simulate(() -> TOTAL), simulate(() -> 1));
    }

    @Test
    void every100TicksEqualsOnce() {
        assertSame(simulate(() -> TOTAL), simulate(() -> 100));
    }

    @Test
    void irregularStepsEqualOnce() {
        Random random = new Random(42);
        assertSame(simulate(() -> TOTAL), simulate(() -> 1 + random.nextInt(5000)));
    }

    @Test
    void dailyStepsEqualOnce() {
        assertSame(simulate(() -> TOTAL), simulate(() -> DAY));
    }
}

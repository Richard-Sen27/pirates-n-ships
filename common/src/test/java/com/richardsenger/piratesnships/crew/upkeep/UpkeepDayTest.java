package com.richardsenger.piratesnships.crew.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.crew.morale.MoraleRules;
import com.richardsenger.piratesnships.crew.provisions.CrewHeadcount;
import com.richardsenger.piratesnships.crew.provisions.ProvisionEffects;
import com.richardsenger.piratesnships.crew.provisions.ProvisionLot;
import com.richardsenger.piratesnships.crew.provisions.ProvisionRules;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionStore;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import com.richardsenger.piratesnships.crew.provisions.ProvisioningState;
import com.richardsenger.piratesnships.station.Stations;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpkeepDayTest {

    private static final MoraleRules.Settings MORALE = MoraleRules.Settings.DEFAULTS;
    private static final UpkeepSettings S = UpkeepSettings.DEFAULTS;
    private static final long DAY = 24000L;
    private static final ProvisionType HARDTACK = ProvisionType.food("hardtack", 4, true, false, 0.25, 0);
    private static final ProvisionType LIME = ProvisionType.food("lime", 2, true, true, 0.25, 0);
    private static final ProvisionType WATER = ProvisionType.water("water", 1, 1.0);
    private static final ProvisionType RUM = ProvisionType.rum("rum", 1, 0.5);

    private static UpkeepDay.Member member(int morale) {
        return new UpkeepDay.Member(UUID.randomUUID(), morale, 0);
    }

    private static UpkeepDay.Member member(int morale, int lowDays) {
        return new UpkeepDay.Member(UUID.randomUUID(), morale, lowDays);
    }

    private static ProvisionEffects day(ProvisionStore store, int crew, ProvisionSettings s) {
        return ProvisionRules.advance(store, ProvisioningState.INITIAL, CrewHeadcount.crew(crew), s, DAY).outcome().effects();
    }

    // ------------------------------------------------------------------ the provisions' effects

    @Test
    void fedCrewKeepsMoraleAndSpeed() {
        ProvisionStore store = ProvisionStore.of(new ProvisionLot(HARDTACK, 10, 0), new ProvisionLot(WATER, 10, 0));
        ProvisionEffects fx = day(store, 2, ProvisionSettings.DEFAULTS);
        assertEquals(0, UpkeepDay.provisionsDelta(fx));
        assertEquals(1.0, fx.workSpeedMultiplier());
        assertFalse(fx.scurvy());
    }

    @Test
    void emptyGalleyCostsHungerAndThirstAndSlowsWork() {
        ProvisionEffects fx = day(ProvisionStore.EMPTY, 2, ProvisionSettings.DEFAULTS);
        assertEquals(-35, UpkeepDay.provisionsDelta(fx)); // 10 hunger + 25 thirst per day
        assertEquals(0.75 * 0.5, fx.workSpeedMultiplier(), 1e-9);
        int base = 100;
        assertEquals(267, Stations.scaledTicks(base, fx.workSpeedMultiplier()));
    }

    @Test
    void rumRaisesMorale() {
        ProvisionStore store = ProvisionStore.of(new ProvisionLot(HARDTACK, 10, 0), new ProvisionLot(WATER, 10, 0),
                new ProvisionLot(RUM, 10, 0));
        assertEquals(4, UpkeepDay.provisionsDelta(day(store, 2, ProvisionSettings.DEFAULTS)));
    }

    @Test
    void scurvyAfterTheOnsetNotBeforeAndNotWithCitrus() {
        ProvisionSettings quick = ProvisionSettings.DEFAULTS.toBuilder().scurvyOnsetDays(0.5).build();
        ProvisionStore hardtack = ProvisionStore.of(new ProvisionLot(HARDTACK, 10, 0), new ProvisionLot(WATER, 10, 0));
        assertTrue(day(hardtack, 2, quick).scurvy());
        assertFalse(day(hardtack, 2, ProvisionSettings.DEFAULTS).scurvy(), "the default onset is 8 days");
        ProvisionStore limes = ProvisionStore.of(new ProvisionLot(LIME, 20, 0), new ProvisionLot(WATER, 10, 0));
        assertFalse(day(limes, 2, quick).scurvy());
    }

    @Test
    void consumptionOffHasNoEffects() {
        ProvisionSettings off = ProvisionSettings.DEFAULTS.toBuilder().consumptionEnabled(false).build();
        ProvisionEffects fx = day(ProvisionStore.EMPTY, 2, off);
        assertEquals(ProvisionEffects.NONE, fx);
        assertEquals(0, UpkeepDay.provisionsDelta(fx));
    }

    @Test
    void scaledTicksKeepsNothingToDoAndCannot() {
        assertEquals(0, Stations.scaledTicks(0, 0.5));
        assertEquals(-1, Stations.scaledTicks(-1, 0.5));
        assertEquals(40, Stations.scaledTicks(40, 1.0));
        assertEquals(80, Stations.scaledTicks(40, 0.5));
        assertEquals(800, Stations.scaledTicks(40, 0.0), "clamped to the slowest speed");
    }

    // ------------------------------------------------------------------ the day's order

    @Test
    void provisionsComeBeforeWagesAndBothBeforeDesertion() {
        // 25 - 10 (hungry) = 15, then -8 (unpaid) = 7: below 20 at this dawn, so the low day counts now
        UpkeepDay.Member m = member(25, 1);
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, S, List.of(m), -10, 0, 0);
        UpkeepDay.MemberResult mr = r.members().get(0);
        assertEquals(-10, mr.provisionsDelta());
        assertEquals(-8, mr.wageDelta());
        assertEquals(7, mr.moraleAfter());
        assertEquals(2, mr.lowDays());
        assertTrue(mr.deserts());
    }

    @Test
    void lowestMoraleIsPaidFirst() {
        UpkeepDay.Member happy = member(80), grumpy = member(30);
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, S, List.of(happy, grumpy), 0, 1, 0);
        assertEquals(UpkeepDay.Pay.UNPAID, r.members().get(0).pay());
        assertEquals(72, r.members().get(0).moraleAfter());
        assertEquals(UpkeepDay.Pay.PAID, r.members().get(1).pay());
        assertEquals(31, r.members().get(1).moraleAfter());
        assertEquals(1, r.paid());
        assertEquals(1, r.unpaid());
    }

    @Test
    void unsetMoraleReadsAsStartAndIsCapped() {
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, S, List.of(member(MoraleRules.UNSET), member(100)), 4, 2, 0);
        assertEquals(75, r.members().get(0).moraleAfter());
        assertEquals(100, r.members().get(1).moraleAfter());
    }

    @Test
    void wagesOffPayNobodyAndCostNothing() {
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, S.withWages(false), List.of(member(50), member(50)), 0, 0, 0);
        assertEquals(0, r.paid());
        assertEquals(0, r.unpaid());
        for (UpkeepDay.MemberResult m : r.members()) {
            assertEquals(UpkeepDay.Pay.NONE, m.pay());
            assertEquals(0, m.wageDelta());
            assertEquals(50, m.moraleAfter());
        }
    }

    @Test
    void moraleDisabledFreezesEverything() {
        MoraleRules.Settings off = new MoraleRules.Settings(false, 70, 5, 10);
        UpkeepDay.Result r = UpkeepDay.settle(off, S, List.of(member(5, 5)), -35, 0, 0);
        assertEquals(70, r.members().get(0).moraleAfter());
        assertFalse(r.members().get(0).deserts());
        assertEquals(0, r.members().get(0).lowDays());
    }

    // ------------------------------------------------------------------ desertion

    @Test
    void desertsAfterTheConfiguredDaysBelowTheThreshold() {
        UpkeepSettings noWages = S.withWages(false);
        UpkeepDay.MemberResult first = UpkeepDay.settle(MORALE, noWages, List.of(member(10, 0)), 0, 0, 0).members().get(0);
        assertEquals(1, first.lowDays());
        assertFalse(first.deserts());
        UpkeepDay.MemberResult second = UpkeepDay.settle(MORALE, noWages, List.of(member(10, 1)), 0, 0, 0).members().get(0);
        assertTrue(second.deserts());
    }

    @Test
    void theThresholdIsStrictAndAGoodDayResetsTheCounter() {
        UpkeepSettings noWages = S.withWages(false);
        UpkeepDay.MemberResult at = UpkeepDay.settle(MORALE, noWages, List.of(member(20, 1)), 0, 0, 0).members().get(0);
        assertEquals(0, at.lowDays());
        assertFalse(at.deserts());
    }

    @Test
    void desertionOffNeverDeserts() {
        UpkeepSettings s = S.withWages(false).withDesertion(false);
        UpkeepDay.MemberResult m = UpkeepDay.settle(MORALE, s, List.of(member(0, 10)), 0, 0, 0).members().get(0);
        assertFalse(m.deserts());
        assertEquals(0, m.lowDays());
    }

    // ------------------------------------------------------------------ mutiny

    @Test
    void mutinyAfterTheConfiguredDaysAndNobodyDesertsMeanwhile() {
        UpkeepSettings s = S.withWages(false).withMutiny(true);
        List<UpkeepDay.Member> crew = List.of(member(10, 5), member(10, 5));
        UpkeepDay.Result day1 = UpkeepDay.settle(MORALE, s, crew, 0, 0, 0);
        assertFalse(day1.mutiny());
        assertEquals(1, day1.shipLowDays());
        assertEquals(0, day1.deserters(), "plotting crew do not desert");
        UpkeepDay.Result day3 = UpkeepDay.settle(MORALE, s, crew, 0, 0, 2);
        assertTrue(day3.mutiny());
        assertEquals(0, day3.shipLowDays());
        assertEquals(0, day3.deserters());
    }

    @Test
    void averageAtOrAboveTheThresholdResetsTheShipCounter() {
        UpkeepSettings s = S.withWages(false).withMutiny(true);
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, s, List.of(member(5), member(25)), 0, 0, 2);
        assertEquals(15.0, r.averageMorale());
        assertFalse(r.mutiny());
        assertEquals(0, r.shipLowDays());
        assertEquals(1, r.members().get(0).lowDays(), "the unhappy member still counts toward desertion");
    }

    @Test
    void mutinyOffNeverMutinies() {
        UpkeepDay.Result r = UpkeepDay.settle(MORALE, S.withWages(false).withDesertion(false), List.of(member(0), member(0)), 0, 0, 99);
        assertFalse(r.mutiny());
        assertEquals(0, r.shipLowDays());
    }
}

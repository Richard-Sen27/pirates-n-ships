package com.richardsenger.piratesnships.crew.upkeep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WageRulesTest {

    private static WageRules.Source<String> src(String key, double distance, long coins) {
        return new WageRules.Source<>(key, distance, coins);
    }

    @Test
    void paysEveryoneWhenThereIsEnough() {
        WageRules.Payment<String> p = WageRules.pay(2, 2, List.of(src("chest", 3, 10)));
        assertEquals(2, p.paid());
        assertEquals(0, p.unpaid());
        assertEquals(4, p.coinsTaken());
        assertEquals(Map.of("chest", 4L), p.takes());
    }

    @Test
    void takesNearestToTheHelmFirstAndEmptiesEachSource() {
        WageRules.Payment<String> p = WageRules.pay(3, 2,
                List.of(src("far", 9, 50), src("near", 1, 3), src("middle", 4, 2)));
        assertEquals(3, p.paid());
        assertEquals(6, p.coinsTaken());
        assertEquals(List.of("near", "middle", "far"), List.copyOf(p.takes().keySet()));
        assertEquals(3L, p.takes().get("near"));
        assertEquals(2L, p.takes().get("middle"));
        assertEquals(1L, p.takes().get("far"));
    }

    @Test
    void tiesKeepTheGivenOrder() {
        WageRules.Payment<String> p = WageRules.pay(1, 2, List.of(src("first", 2, 5), src("second", 2, 5)));
        assertEquals(Map.of("first", 2L), p.takes());
    }

    @Test
    void partialPayIsInWholeWagesOnly() {
        WageRules.Payment<String> p = WageRules.pay(2, 2, List.of(src("chest", 1, 3)));
        assertEquals(1, p.paid());
        assertEquals(1, p.unpaid());
        assertEquals(2, p.coinsTaken());
        assertEquals(Map.of("chest", 2L), p.takes());

        WageRules.Payment<String> one = WageRules.pay(2, 2, List.of(src("chest", 1, 1)));
        assertEquals(0, one.paid());
        assertEquals(2, one.unpaid());
        assertTrue(one.takes().isEmpty(), "a single coin is not a wage and stays");
    }

    @Test
    void noCoinsNobodyPaid() {
        WageRules.Payment<String> p = WageRules.pay(3, 2, List.of());
        assertEquals(0, p.paid());
        assertEquals(3, p.unpaid());
        assertEquals(0, p.coinsTaken());
    }

    @Test
    void freeCrewCostsNothing() {
        WageRules.Payment<String> p = WageRules.pay(4, 0, List.of(src("chest", 1, 10)));
        assertEquals(4, p.paid());
        assertEquals(0, p.coinsTaken());
        assertTrue(p.takes().isEmpty());
    }
}

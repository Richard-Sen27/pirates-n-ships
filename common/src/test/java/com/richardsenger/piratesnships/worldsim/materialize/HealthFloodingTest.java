package com.richardsenger.piratesnships.worldsim.materialize;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthFloodingTest {

    /** A hold at the bottom (40 blocks), two cabins above it (10 each, the second listed first), a deckhouse (20). */
    private static final List<HealthFlooding.Tank> HULL = List.of(
            new HealthFlooding.Tank(0, 5, 20),
            new HealthFlooding.Tank(1, 3, 10),
            new HealthFlooding.Tank(2, 0, 40),
            new HealthFlooding.Tank(3, 3, 10));

    private static double sum(Map<Integer, Double> v) {
        return v.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    @Test
    void fullHealthFloodsNothing() {
        assertTrue(HealthFlooding.volumes(HULL, 1.0).isEmpty());
        assertTrue(HealthFlooding.volumes(HULL, 1.5).isEmpty(), "clamped");
        assertTrue(HealthFlooding.volumes(List.of(), 0.2).isEmpty(), "no compartments");
    }

    @Test
    void theLowestCompartmentFillsFirst() {
        // 40 % of 80 = 32 blocks, all in the hold
        Map<Integer, Double> v = HealthFlooding.volumes(HULL, 0.6);
        assertEquals(Map.of(2, 32.0), v);
    }

    @Test
    void waterRisesIntoTheNextCompartmentsByHeightThenId() {
        // 70 % of 80 = 56: the hold (40), then the cabin with the lower id (10), then 6 into the other cabin
        Map<Integer, Double> v = HealthFlooding.volumes(HULL, 0.3);
        assertEquals(40.0, v.get(2), 1e-9);
        assertEquals(10.0, v.get(1), 1e-9);
        assertEquals(6.0, v.get(3), 1e-9);
        assertEquals(3, v.size(), v.toString());
        assertEquals(56.0, sum(v), 1e-9);
    }

    @Test
    void zeroHealthFillsEverything() {
        Map<Integer, Double> v = HealthFlooding.volumes(HULL, -0.1);
        assertEquals(80.0, sum(v), 1e-9);
        assertEquals(Map.of(0, 20.0, 1, 10.0, 2, 40.0, 3, 10.0), v);
    }

    @Test
    void theFloodedFractionMatchesTheHealth() {
        for (double health : new double[] {0.95, 0.8, 0.55, 0.25, 0.05}) {
            double fraction = sum(HealthFlooding.volumes(HULL, health)) / 80.0;
            assertEquals(1.0 - health, fraction, 1e-9, "health " + health);
            assertEquals(health, 1.0 - EndingRules.floodFraction(sum(HealthFlooding.volumes(HULL, health)), 80.0), 1e-9,
                    "read back as the record's health");
        }
    }
}

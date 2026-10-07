package com.richardsenger.piratesnships.trade.cargo;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** CW1: load state, mass per state, unit conversion, vanilla force, ship capacity. */
class CargoMassTest {

    private static final CargoWeight.Params ON = CargoWeight.Params.DEFAULTS;
    private static final CargoWeight.Params OFF = new CargoWeight.Params(false, 1.0, 0.25, 0.33, 0.75, 1.0);
    private static final CargoWeight.Params DOUBLE = new CargoWeight.Params(true, 2.0, 0.25, 0.33, 0.75, 1.0);

    @Test
    void loadStateRoundsToTheNearestQuarter() {
        double n = 1000;
        assertEquals(0, CargoMass.loadState(0, n));
        assertEquals(0, CargoMass.loadState(-5, n));
        assertEquals(0, CargoMass.loadState(124.9, n), "just under an eighth stays empty");
        assertEquals(1, CargoMass.loadState(125, n), "an eighth rounds up to a quarter");
        assertEquals(1, CargoMass.loadState(374.9, n));
        assertEquals(2, CargoMass.loadState(375, n));
        assertEquals(2, CargoMass.loadState(500, n));
        assertEquals(3, CargoMass.loadState(625, n));
        assertEquals(3, CargoMass.loadState(874.9, n));
        assertEquals(4, CargoMass.loadState(875, n));
        assertEquals(4, CargoMass.loadState(1000, n));
        assertEquals(4, CargoMass.loadState(5000, n), "heavier than nominal counts as full");
        assertEquals(0, CargoMass.loadState(500, 0), "no nominal weight, no load");
        assertEquals(0, CargoMass.loadState(Double.NaN, n));
    }

    @Test
    void profileLoadStateUsesToggleAndFactor() {
        CargoMass.Profile crate = CargoMass.CRATE;
        assertEquals(2, crate.loadState(512, ON), "a crate of sugar (2,048 × 0.25) is half full");
        assertEquals(4, crate.loadState(512, DOUBLE), "weight factor 2 doubles the effective weight");
        assertEquals(0, crate.loadState(2048, OFF), "toggle off: always empty");
    }

    @Test
    void massPerStateWithTheUnitConversion() {
        assertEquals(0.02, CargoMass.KPG_PER_UNIT);
        // crate: 1,024 units nominal → 20.48 kpg when full, on top of the plank-like 0.5
        assertEquals(1024.0, CargoMass.CRATE.nominalWeight());
        assertEquals(20.48, CargoMass.CRATE.fullExtraMass(), 1e-9);
        double[] crate = {0.5, 5.62, 10.74, 15.86, 20.98};
        for (int l = 0; l <= 4; l++) {
            assertEquals(crate[l], CargoMass.CRATE.mass(l), 1e-9, "crate load " + l);
        }
        assertEquals(0.5, CargoMass.CRATE.mass(-1), 1e-9, "clamped below");
        assertEquals(20.98, CargoMass.CRATE.mass(9), 1e-9, "clamped above");
        assertEquals(0.5 + 15.36, CargoMass.BARREL.mass(4), 1e-9);
        assertEquals(0.5 + 8.64, CargoMass.PANTRY.mass(4), 1e-9);
        assertEquals(2.0, CargoMass.WATER_BARREL.mass(0), 1e-9);
        assertEquals(2.32, CargoMass.WATER_BARREL.mass(4), 1e-9);
        // the mass of a state equals the mass its nominal content would have
        assertEquals(CargoMass.massOf(CargoMass.CRATE.nominalWeight() / 2, ON), CargoMass.CRATE.mass(2) - CargoMass.CRATE.mass(0), 1e-9);
    }

    @Test
    void vanillaForceIsTheWeightOfTheEquivalentMass() {
        // a chest of 27 stacks of a non-good (0.25 each): 432 units → 8.64 kpg → 95.04 N at gravity 11
        double w = 27 * 64 * 0.25;
        assertEquals(8.64, CargoMass.massOf(w, ON), 1e-9);
        assertEquals(95.04, CargoMass.forceOf(w, ON, 11), 1e-9);
        assertEquals(190.08, CargoMass.forceOf(w, DOUBLE, 11), 1e-9);
        assertEquals(0.0, CargoMass.forceOf(w, OFF, 11));
        assertEquals(0.0, CargoMass.forceOf(-3, ON, 11));
    }

    @Test
    void shipCapacityPerBlock() {
        assertEquals(5440.0, CargoWeight.shipCapacity(680, CargoWeight.CAPACITY_PER_BLOCK));
        assertEquals(0.0, CargoWeight.shipCapacity(-1, 8));
        // the starter sloop's six containers at their nominal weight make it heavily laden
        double sloop = 3 * CargoMass.CRATE.nominalWeight() + 3 * CargoMass.BARREL.nominalWeight();
        double cap = CargoWeight.shipCapacity(680, CargoWeight.CAPACITY_PER_BLOCK);
        assertEquals(CargoWeight.LoadLevel.HEAVILY_LADEN, CargoWeight.LoadLevel.of(sloop, cap, ON));
        assertEquals(CargoWeight.LoadLevel.LADEN, CargoWeight.LoadLevel.of(2 * CargoMass.CRATE.nominalWeight(), cap, ON));
        assertEquals(CargoWeight.LoadLevel.LIGHT, CargoWeight.LoadLevel.of(CargoMass.CRATE.nominalWeight(), cap, ON));
        assertEquals(CargoWeight.LoadLevel.OVERLOADED, CargoWeight.LoadLevel.of(sloop + 500, cap, ON));
    }

    @Test
    void physicsJsonHasOneOverridePerLoad() {
        JsonObject json = CargoPhysicsData.json(ResourceLocation.parse("pirates_n_ships:cargo_crate"), CargoMass.CRATE);
        assertEquals("pirates_n_ships:cargo_crate", json.get("selector").getAsString());
        assertTrue(json.get("priority").getAsInt() > 1000, "after Sable's tag definitions (priority 1000)");
        assertEquals(0.5, json.getAsJsonObject("properties").get("sable:mass").getAsDouble());
        JsonObject overrides = json.getAsJsonObject("overrides");
        assertEquals(4, overrides.size());
        for (int l = 1; l <= 4; l++) {
            assertEquals(CargoMass.CRATE.mass(l), overrides.getAsJsonObject("load=" + l).get("sable:mass").getAsDouble(), 1e-4);
        }
    }
}

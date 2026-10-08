package com.richardsenger.piratesnships.combat.cannon.npc;

import com.richardsenger.piratesnships.law.flag.Faction;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.law.flag.ShipStance;
import com.richardsenger.piratesnships.ship.decor.flag.FlagReading;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WS4a: whom a ship's gun crews fire at by themselves. */
class ShipHostilityTest {

    private static boolean hostile(Faction self, FlagReading target, boolean wanted, boolean coverBlown) {
        return ShipHostility.hostile(self, target, wanted, ShipStance.of(target.shown(), target.isStruck(), coverBlown));
    }

    private static boolean hostile(Faction self, FlagKind flying) {
        return hostile(self, flying == FlagKind.NONE ? FlagReading.NO_FLAG : FlagReading.flying(flying), false, false);
    }

    @Test
    void navyFiresOnTheJollyRogerTheUnmaskedAndTheWanted() {
        assertTrue(hostile(Faction.NAVY, FlagKind.JOLLY_ROGER));
        assertFalse(hostile(Faction.NAVY, FlagKind.MERCHANT));
        assertFalse(hostile(Faction.NAVY, FlagKind.NAVY));
        assertFalse(hostile(Faction.NAVY, FlagKind.NONE));
        assertFalse(hostile(Faction.NAVY, FlagKind.CUSTOM));
        assertTrue(hostile(Faction.NAVY, FlagReading.flying(FlagKind.NAVY), false, true), "false colours seen through");
        assertTrue(hostile(Faction.NAVY, FlagReading.flying(FlagKind.MERCHANT), true, false), "a wanted or bountied owner");
        assertTrue(hostile(Faction.NAVY, FlagReading.NO_FLAG, true, false));
    }

    @Test
    void piratesFireOnTheNavyAndOnMerchants() {
        assertTrue(hostile(Faction.PIRATES, FlagKind.NAVY));
        assertTrue(hostile(Faction.PIRATES, FlagKind.MERCHANT));
        assertTrue(hostile(Faction.PIRATES, FlagKind.NONE), "an unflagged ship counts as a merchant (§4.7)");
        assertFalse(hostile(Faction.PIRATES, FlagKind.JOLLY_ROGER));
        assertFalse(hostile(Faction.PIRATES, FlagKind.CUSTOM), "a banner flag is neutral");
        assertFalse(hostile(Faction.PIRATES, FlagReading.flying(FlagKind.JOLLY_ROGER), true, false), "a wanted pirate is a friend");
    }

    @Test
    void merchantsFireOnlyOnTheJollyRoger() {
        assertTrue(hostile(Faction.MERCHANTS, FlagKind.JOLLY_ROGER));
        for (FlagKind k : new FlagKind[]{FlagKind.NONE, FlagKind.MERCHANT, FlagKind.NAVY, FlagKind.CUSTOM}) {
            assertFalse(hostile(Faction.MERCHANTS, k), "merchants vs " + k);
        }
        assertFalse(hostile(Faction.MERCHANTS, FlagReading.flying(FlagKind.MERCHANT), true, true), "merchants do not hunt the wanted");
    }

    @Test
    void aSurrenderedShipIsNeverATarget() {
        for (Faction f : Faction.values()) {
            for (FlagKind k : FlagKind.values()) {
                if (k == FlagKind.NONE) continue;
                assertFalse(hostile(f, FlagReading.struck(k), true, true), f + " vs struck " + k);
                assertFalse(ShipHostility.hostile(f, FlagReading.flying(k), true, ShipStance.SURRENDERED), f + " vs surrendered " + k);
            }
        }
    }

    @Test
    void aShipFightsForTheFactionOfItsFlag() {
        assertEquals(Faction.NAVY, ShipHostility.factionOf(FlagReading.flying(FlagKind.NAVY)));
        assertEquals(Faction.PIRATES, ShipHostility.factionOf(FlagReading.flying(FlagKind.JOLLY_ROGER)));
        assertEquals(Faction.MERCHANTS, ShipHostility.factionOf(FlagReading.flying(FlagKind.MERCHANT)));
        assertEquals(Faction.MERCHANTS, ShipHostility.factionOf(FlagReading.flying(FlagKind.CUSTOM)));
        assertEquals(Faction.MERCHANTS, ShipHostility.factionOf(FlagReading.NO_FLAG));
        assertEquals(Faction.MERCHANTS, ShipHostility.factionOf(FlagReading.struck(FlagKind.JOLLY_ROGER)), "struck shows nothing");
    }
}

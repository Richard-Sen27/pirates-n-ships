package com.richardsenger.piratesnships.mob.monsters;

import com.richardsenger.piratesnships.trade.market.PortKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterRulesTest {

    private static final UUID SHIP = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void monstersHuntPeopleOnLandAndInTheWater() {
        assertTrue(MonsterRules.hunts(true, true, null, null));
        assertTrue(MonsterRules.hunts(true, true, null, SHIP), "a monster on a deck hunts a person ashore");
    }

    @Test
    void theToggleAndNonPeopleStopTheHunt() {
        assertFalse(MonsterRules.hunts(false, true, null, null), "monsters_attack_npcs off");
        assertFalse(MonsterRules.hunts(true, false, null, null), "pirates and navy are no prey");
    }

    @Test
    void aDeckIsHuntedOnlyFromTheSameShip() {
        assertFalse(MonsterRules.hunts(true, true, SHIP, null), "from the quay or the sea");
        assertFalse(MonsterRules.hunts(true, true, SHIP, OTHER), "from another ship");
        assertTrue(MonsterRules.hunts(true, true, SHIP, SHIP), "aboard the same ship");
    }

    @Test
    void armedCrewFightsUnarmedRunsBusyStands() {
        assertEquals(MonsterRules.Response.FIGHT, MonsterRules.respond(true, true, false));
        assertEquals(MonsterRules.Response.FLEE, MonsterRules.respond(true, false, false));
        assertEquals(MonsterRules.Response.STAND, MonsterRules.respond(true, true, true), "at a station");
        assertEquals(MonsterRules.Response.STAND, MonsterRules.respond(false, true, false), "crew_fight_monsters off");
        assertEquals(MonsterRules.Response.STAND, MonsterRules.respond(false, false, false), "crew_fight_monsters off");
    }

    @Test
    void theFenceOfAPirateIslandDoesNotFearPirates() {
        assertFalse(MonsterRules.fearsPirates(PortKind.PIRATE_ISLAND, false));
        assertTrue(MonsterRules.fearsPirates(PortKind.PIRATE_ISLAND, true), "fears_pirates_at_islands on");
        assertTrue(MonsterRules.fearsPirates(PortKind.SEAFARER_VILLAGE, false));
        assertTrue(MonsterRules.fearsPirates(PortKind.NAVY_OUTPOST, false));
        assertTrue(MonsterRules.fearsPirates(null, false), "unknown port: as a sailor");
    }
}

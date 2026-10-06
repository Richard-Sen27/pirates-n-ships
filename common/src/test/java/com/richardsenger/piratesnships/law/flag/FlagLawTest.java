package com.richardsenger.piratesnships.law.flag;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class FlagLawTest {

    @ParameterizedTest(name = "{0} sees {1} -> {2} (surrender off: {3})")
    @CsvSource({
            "NAVY, NONE, NEUTRAL, NEUTRAL",
            "NAVY, MERCHANT, NEUTRAL, NEUTRAL",
            "NAVY, NAVY, FRIENDLY, FRIENDLY",
            "NAVY, JOLLY_ROGER, HOSTILE, HOSTILE",
            "NAVY, CUSTOM, NEUTRAL, NEUTRAL",
            "PIRATES, NONE, NEUTRAL, NEUTRAL",
            "PIRATES, MERCHANT, NEUTRAL, NEUTRAL",
            "PIRATES, NAVY, HOSTILE, HOSTILE",
            "PIRATES, JOLLY_ROGER, FRIENDLY, FRIENDLY",
            "PIRATES, CUSTOM, NEUTRAL, NEUTRAL",
            "MERCHANTS, NONE, NEUTRAL, NEUTRAL",
            "MERCHANTS, MERCHANT, NEUTRAL, NEUTRAL",
            "MERCHANTS, NAVY, FRIENDLY, FRIENDLY",
            "MERCHANTS, JOLLY_ROGER, MAY_SURRENDER, HOSTILE",
            "MERCHANTS, CUSTOM, NEUTRAL, NEUTRAL",
    })
    void reactionTable(Faction observer, FlagKind flag, Reaction withSurrender, Reaction withoutSurrender) {
        assertEquals(withSurrender, FlagLaw.react(observer, flag, true));
        assertEquals(withoutSurrender, FlagLaw.react(observer, flag, false));
    }

    @Test
    void tableCoversEveryCombination() {
        for (Faction f : Faction.values()) for (FlagKind k : FlagKind.values()) assertNotNull(FlagLaw.react(f, k, true));
        assertEquals(15, Faction.values().length * FlagKind.values().length);
    }

    static final int MIN = 100;

    @Test
    void navyFlagNeedsStandingAndNoBounty() {
        assertFalse(FlagLaw.isFalseFlag(FlagKind.NAVY, new FlagLaw.CaptainStanding(100, false), MIN));
        assertFalse(FlagLaw.isFalseFlag(FlagKind.NAVY, new FlagLaw.CaptainStanding(500, false), MIN));
        assertTrue(FlagLaw.isFalseFlag(FlagKind.NAVY, new FlagLaw.CaptainStanding(99, false), MIN));
        assertTrue(FlagLaw.isFalseFlag(FlagKind.NAVY, new FlagLaw.CaptainStanding(500, true), MIN));
    }

    @ParameterizedTest
    @EnumSource(value = FlagKind.class, names = {"NONE", "MERCHANT", "CUSTOM"})
    void harmlessFlagsAreFalseOnlyWithBounty(FlagKind flag) {
        assertFalse(FlagLaw.isFalseFlag(flag, new FlagLaw.CaptainStanding(-1000, false), MIN));
        assertTrue(FlagLaw.isFalseFlag(flag, new FlagLaw.CaptainStanding(1000, true), MIN));
    }

    @Test
    void jollyRogerIsNeverFalse() {
        assertFalse(FlagLaw.isFalseFlag(FlagKind.JOLLY_ROGER, new FlagLaw.CaptainStanding(1000, true), MIN));
        assertFalse(FlagLaw.isFalseFlag(FlagKind.JOLLY_ROGER, new FlagLaw.CaptainStanding(-1000, false), MIN));
    }

    @Test
    void consequences() {
        assertEquals(CrimeType.CAUGHT_FALSE_COLORS, FlagLaw.crimeWhenCaughtUnderFalseColors());
        assertEquals(CrimeType.SEEN_UNDER_JOLLY_ROGER, FlagLaw.crimeWhenSeen(FlagKind.JOLLY_ROGER, Faction.NAVY));
        assertEquals(CrimeType.SEEN_UNDER_JOLLY_ROGER, FlagLaw.crimeWhenSeen(FlagKind.JOLLY_ROGER, Faction.MERCHANTS));
        assertNull(FlagLaw.crimeWhenSeen(FlagKind.JOLLY_ROGER, Faction.PIRATES));
        for (FlagKind k : FlagKind.values()) {
            if (k != FlagKind.JOLLY_ROGER) assertNull(FlagLaw.crimeWhenSeen(k, Faction.NAVY));
        }
    }

    @Test
    void attackingShips() {
        for (FlagKind k : FlagKind.values()) {
            assertEquals(CrimeType.ATTACK_STRUCK_COLORS, FlagLaw.crimeForAttackingShip(k, true, false), "struck " + k);
            assertEquals(CrimeType.ATTACK_STRUCK_COLORS, FlagLaw.crimeForAttackingShip(k, true, true));
        }
        assertNull(FlagLaw.crimeForAttackingShip(FlagKind.JOLLY_ROGER, false, false), "attacking pirates is legal");
        assertEquals(CrimeType.ATTACK_NEUTRAL_SHIP, FlagLaw.crimeForAttackingShip(FlagKind.MERCHANT, false, false));
        assertEquals(CrimeType.ATTACK_NEUTRAL_SHIP, FlagLaw.crimeForAttackingShip(FlagKind.NAVY, false, false),
                "a fake navy ship is still a neutral victim");
        assertEquals(CrimeType.ATTACK_NAVY, FlagLaw.crimeForAttackingShip(FlagKind.NAVY, false, true));
    }
}

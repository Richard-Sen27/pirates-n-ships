package com.richardsenger.piratesnships.law.flag;

import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FL2's pure rules: the interim false-colours rule, the crime for a ship hit, and a ship's stance. */
class FlagWorldRulesTest {

    /** Standing 0 for everyone (no reputation yet), default minimum standing 100. */
    private static final FlagLaw.CaptainStanding NOBODY = new FlagLaw.CaptainStanding(0, false);
    private static final int MIN_STANDING = 100;

    @Test
    void navyFlagIsFalseColoursFromTheWantedThresholdUp() {
        assertFalse(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.CLEAN, 1), "clean captain");
        assertTrue(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.SUSPECT, 1), "suspect captain");
        assertTrue(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.NOTORIOUS, 1), "notorious captain");
        assertFalse(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.SUSPECT, 2), "suspect below threshold 2");
        assertTrue(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.WANTED, 2), "wanted at threshold 2");
        assertTrue(FlagLaw.fliesFalseColours(FlagKind.NAVY, NOBODY, MIN_STANDING, WantedLevel.CLEAN, 0), "threshold 0: everyone");
    }

    @Test
    void enoughStandingMakesTheNavyFlagLegitimateUnlessThereIsABounty() {
        var trusted = new FlagLaw.CaptainStanding(150, false);
        assertFalse(FlagLaw.fliesFalseColours(FlagKind.NAVY, trusted, MIN_STANDING, WantedLevel.WANTED, 1));
        var trustedWithBounty = new FlagLaw.CaptainStanding(150, true);
        assertTrue(FlagLaw.fliesFalseColours(FlagKind.NAVY, trustedWithBounty, MIN_STANDING, WantedLevel.WANTED, 1));
    }

    @ParameterizedTest
    @EnumSource(value = FlagKind.class, names = {"NONE", "MERCHANT", "JOLLY_ROGER", "CUSTOM"})
    void onlyTheNavyFlagIsJudgedInTheWorld(FlagKind flag) {
        var bounty = new FlagLaw.CaptainStanding(0, true);
        assertFalse(FlagLaw.fliesFalseColours(flag, bounty, MIN_STANDING, WantedLevel.NOTORIOUS, 0));
    }

    @Test
    void hittingAStruckShipIsAttackingStruckColoursWhateverItsFlag() {
        for (FlagKind k : FlagKind.values()) {
            if (k == FlagKind.NONE) continue;
            assertEquals(CrimeType.ATTACK_STRUCK_COLORS, FlagLaw.crimeForShipHit(k, true, false), k.name());
        }
    }

    @Test
    void hittingANeutralShipIsAttackingANeutralShip() {
        assertEquals(CrimeType.ATTACK_NEUTRAL_SHIP, FlagLaw.crimeForShipHit(FlagKind.MERCHANT, false, false));
        assertEquals(CrimeType.ATTACK_NEUTRAL_SHIP, FlagLaw.crimeForShipHit(FlagKind.CUSTOM, false, false));
    }

    @Test
    void hittingPiratesNavyFlagsUnflaggedOrOwnShipsIsNoCrime() {
        assertNull(FlagLaw.crimeForShipHit(FlagKind.JOLLY_ROGER, false, false));
        assertNull(FlagLaw.crimeForShipHit(FlagKind.NAVY, false, false));
        assertNull(FlagLaw.crimeForShipHit(FlagKind.NONE, false, false));
        assertNull(FlagLaw.crimeForShipHit(FlagKind.MERCHANT, false, true), "own merchant ship");
        assertNull(FlagLaw.crimeForShipHit(FlagKind.JOLLY_ROGER, true, true), "own struck ship");
    }

    @Test
    void stanceSurrenderBeatsBlownCoverBeatsJollyRoger() {
        assertEquals(ShipStance.SURRENDERED, ShipStance.of(FlagKind.NONE, true, true));
        assertEquals(ShipStance.UNMASKED, ShipStance.of(FlagKind.NAVY, false, true));
        assertEquals(ShipStance.UNMASKED, ShipStance.of(FlagKind.MERCHANT, false, true), "a blown cover stays blown under another flag");
        assertEquals(ShipStance.JOLLY_ROGER, ShipStance.of(FlagKind.JOLLY_ROGER, false, false));
        assertEquals(ShipStance.NONE, ShipStance.of(FlagKind.NAVY, false, false));
        assertEquals(ShipStance.NONE, ShipStance.of(FlagKind.MERCHANT, false, false));
        assertEquals(ShipStance.NONE, ShipStance.of(FlagKind.NONE, false, false));
    }
}

package com.richardsenger.piratesnships.worldsim.captain;

import com.richardsenger.piratesnships.ship.decor.NameplateText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TPL2: the captain's ship carries his name. */
class CaptainShipNamesTest {

    @Test
    void fullNameInThePossessive() {
        assertEquals("Black-Tooth Bartholomew Crowe's Sea Wolf", CaptainShipNames.of("Black-Tooth Bartholomew Crowe", "Sea Wolf"));
    }

    @Test
    void aNameEndingInSTakesABareApostrophe() {
        assertEquals("Mad Silas' Night Shark", CaptainShipNames.of("Mad Silas", "Night Shark"));
        assertEquals("Crowe's", CaptainShipNames.possessive("Crowe"));
    }

    @Test
    void tooLongFallsBackToTheSurname() {
        String captain = "Salt-Beard Bartholomew Montgomery-Fitzwilliam";
        String full = CaptainShipNames.possessive(captain) + " Widow's Revenge";
        assertTrue(full.length() > CaptainShipNames.MAX_LENGTH, "the case is long enough: " + full.length());
        assertEquals("Montgomery-Fitzwilliam's Widow's Revenge", CaptainShipNames.of(captain, "Widow's Revenge"));
    }

    @Test
    void stillTooLongIsCut() {
        String captain = "X " + "Y".repeat(60);
        String name = CaptainShipNames.of(captain, "Sea Wolf");
        assertEquals(CaptainShipNames.MAX_LENGTH, name.length());
        assertTrue(name.startsWith("YYY"), name);
    }

    @Test
    void blankCaptainKeepsTheShipsName() {
        assertEquals("Sea Wolf", CaptainShipNames.of("  ", "Sea Wolf"));
        assertEquals("Sea Wolf", CaptainShipNames.of(null, " Sea Wolf "));
    }

    @Test
    void neverLongerThanANameplate() {
        assertEquals(NameplateText.MAX_LENGTH, CaptainShipNames.MAX_LENGTH);
    }
}

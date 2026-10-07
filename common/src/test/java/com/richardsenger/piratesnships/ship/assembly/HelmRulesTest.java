package com.richardsenger.piratesnships.ship.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.richardsenger.piratesnships.ship.assembly.HelmRules.Role;
import org.junit.jupiter.api.Test;

class HelmRulesTest {

    @Test
    void aBodyWithoutOurPointerIsNotAShip() {
        assertEquals(Role.NOT_A_SHIP, HelmRules.role(false, "a", true, "a"));
        assertEquals(Role.NOT_A_SHIP, HelmRules.role(false, null, false, "a"));
    }

    @Test
    void theRememberedHelmSteers() {
        assertEquals(Role.STEERING, HelmRules.role(true, "a", true, "a"));
    }

    @Test
    void anotherHelmIsASecondHelmWhileTheFirstStands() {
        assertEquals(Role.SECOND, HelmRules.role(true, "a", true, "b"));
    }

    @Test
    void aHelmlessShipTakesTheNextHelm() {
        assertEquals(Role.ATTACHES, HelmRules.role(true, null, false, "b"));
        // the remembered helm was broken: its position holds no helm any more
        assertEquals(Role.ATTACHES, HelmRules.role(true, "a", false, "b"));
        // a helm placed again where the old one stood
        assertEquals(Role.ATTACHES, HelmRules.role(true, "a", false, "a"));
    }

    @Test
    void onlySteeringAndAttachingHelmsSteer() {
        assertTrue(HelmRules.steers(Role.STEERING));
        assertTrue(HelmRules.steers(Role.ATTACHES));
        assertFalse(HelmRules.steers(Role.SECOND));
        assertFalse(HelmRules.steers(Role.NOT_A_SHIP));
    }
}

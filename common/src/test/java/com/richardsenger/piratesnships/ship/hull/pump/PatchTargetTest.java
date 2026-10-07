package com.richardsenger.piratesnships.ship.hull.pump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchTargetTest {

    @Test
    void onlyAnOpenBreachOnAShipTakesAPatch() {
        assertEquals(PatchTarget.Result.OK, PatchTarget.check(true, true, true, true));
        assertEquals(PatchTarget.Result.NOT_A_BREACH, PatchTarget.check(true, true, false, true));
        assertEquals(PatchTarget.Result.NOT_ON_SHIP, PatchTarget.check(true, false, false, true));
        assertEquals(PatchTarget.Result.BLOCKED, PatchTarget.check(true, true, true, false));
    }

    @Test
    void disabledWinsOverEverything() {
        assertEquals(PatchTarget.Result.DISABLED, PatchTarget.check(false, true, true, true));
        assertEquals(PatchTarget.Result.DISABLED, PatchTarget.check(false, false, false, false));
    }
}

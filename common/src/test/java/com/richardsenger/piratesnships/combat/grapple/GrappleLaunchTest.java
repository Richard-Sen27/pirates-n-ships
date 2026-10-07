package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Mode;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.MusketRefusal;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Powder;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrappleLaunchTest {

    private static final double EPS = 1e-9;

    // ---- mode ----

    @Test
    void otherHandPicksTheMode() {
        assertEquals(Mode.THROW, GrappleLaunch.mode(Weapon.NONE, true, true));
        assertEquals(Mode.CROSSBOW, GrappleLaunch.mode(Weapon.CROSSBOW, true, true));
        assertEquals(Mode.MUSKET, GrappleLaunch.mode(Weapon.MUSKET, true, true));
    }

    @Test
    void switchedOffModesThrow() {
        assertEquals(Mode.THROW, GrappleLaunch.mode(Weapon.CROSSBOW, false, true));
        assertEquals(Mode.THROW, GrappleLaunch.mode(Weapon.MUSKET, true, false));
        assertEquals(Mode.CROSSBOW, GrappleLaunch.mode(Weapon.CROSSBOW, true, false), "the musket toggle does not touch the crossbow");
    }

    // ---- speed and rope ----

    @Test
    void speedPerMode() {
        assertEquals(1.5, GrappleLaunch.speed(Mode.THROW, 1.5, 1.6, 2.4), EPS);
        assertEquals(2.4, GrappleLaunch.speed(Mode.CROSSBOW, 1.5, 1.6, 2.4), EPS);
        assertEquals(3.6, GrappleLaunch.speed(Mode.MUSKET, 1.5, 1.6, 2.4), EPS);
    }

    @Test
    void ropeGrowsWithTheModeButNeverShrinks() {
        assertEquals(24, GrappleLaunch.ropeLength(Mode.THROW, 24, 36, 48), EPS);
        assertEquals(36, GrappleLaunch.ropeLength(Mode.CROSSBOW, 24, 36, 48), EPS);
        assertEquals(48, GrappleLaunch.ropeLength(Mode.MUSKET, 24, 36, 48), EPS);
        assertEquals(30, GrappleLaunch.ropeLength(Mode.CROSSBOW, 30, 20, 48), EPS, "a weapon rope shorter than the thrown one");
    }

    @Test
    void weaponsAreSteadierThanAThrow() {
        assertTrue(GrappleLaunch.inaccuracy(Mode.CROSSBOW) < GrappleLaunch.inaccuracy(Mode.THROW));
        assertEquals(GrappleLaunch.inaccuracy(Mode.CROSSBOW), GrappleLaunch.inaccuracy(Mode.MUSKET));
    }

    // ---- draw ----

    @Test
    void drawNeedsTheFullDrawTime() {
        assertFalse(GrappleLaunch.drawn(0, 25));
        assertFalse(GrappleLaunch.drawn(24, 25));
        assertTrue(GrappleLaunch.drawn(25, 25));
        assertTrue(GrappleLaunch.drawn(400, 25));
        assertTrue(GrappleLaunch.drawn(0, 0), "a zero draw time shoots on any release");
        assertTrue(GrappleLaunch.drawn(0, -3));
    }

    // ---- powder ----

    @Test
    void powderIsTakenInSurvival() {
        assertEquals(Powder.CONSUME, GrappleLaunch.powder(false, 1, true));
        assertEquals(Powder.CONSUME, GrappleLaunch.powder(false, 64, true));
        assertEquals(Powder.MISSING, GrappleLaunch.powder(false, 0, true));
    }

    @Test
    void creativeOrNoPowderRuleIsFree() {
        assertEquals(Powder.FREE, GrappleLaunch.powder(true, 0, true));
        assertEquals(Powder.FREE, GrappleLaunch.powder(false, 0, false));
    }

    @Test
    void musketRefusalOrder() {
        assertEquals(MusketRefusal.LOADED, GrappleLaunch.musketRefusal(true, true, Powder.MISSING));
        assertEquals(MusketRefusal.COOLDOWN, GrappleLaunch.musketRefusal(false, true, Powder.MISSING));
        assertEquals(MusketRefusal.NO_POWDER, GrappleLaunch.musketRefusal(false, false, Powder.MISSING));
        assertEquals(MusketRefusal.NONE, GrappleLaunch.musketRefusal(false, false, Powder.CONSUME));
        assertEquals(MusketRefusal.NONE, GrappleLaunch.musketRefusal(false, false, Powder.FREE));
    }
}

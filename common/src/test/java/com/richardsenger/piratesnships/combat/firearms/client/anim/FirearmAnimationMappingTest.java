package com.richardsenger.piratesnships.combat.firearms.client.anim;

import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmRules;
import com.richardsenger.piratesnships.combat.firearms.client.anim.FirearmAnimationMapping.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirearmAnimationMappingTest {

    private static final int AIM = FirearmRules.AIM_SESSION_TICKS;
    private static final int LOAD = FirearmRules.LOAD_SESSION_TICKS;

    @Test
    void notUsingAGunShowsNothing() {
        assertNull(FirearmAnimationMapping.forUse(null, AIM));
        assertNull(FirearmAnimationMapping.forUse(FirearmKind.PISTOL, 0));
    }

    @Test
    void aimSessionsPickTheGunsAimAnimation() {
        Pose pistol = FirearmAnimationMapping.forUse(FirearmKind.PISTOL, AIM - 5);
        assertEquals(new Pose(FirearmAnimationMapping.PISTOL_AIM, true, 5), pistol);
        Pose musket = FirearmAnimationMapping.forUse(FirearmKind.MUSKET, AIM);
        assertEquals(new Pose(FirearmAnimationMapping.MUSKET_AIM, true, 0), musket);
    }

    @Test
    void loadingSessionsPickTheGunsReloadAnimationEvenAfterTheGunIsLoaded() {
        assertEquals(new Pose(FirearmAnimationMapping.PISTOL_RELOAD, false, 0), FirearmAnimationMapping.forUse(FirearmKind.PISTOL, LOAD));
        // held past the reload time: still the loading session (the reload animation has ended, the layer just idles)
        assertEquals(new Pose(FirearmAnimationMapping.MUSKET_RELOAD, false, 400), FirearmAnimationMapping.forUse(FirearmKind.MUSKET, LOAD - 400));
    }

    @Test
    void theSessionBoundaryMatchesFirearmRules() {
        assertTrue(FirearmAnimationMapping.forUse(FirearmKind.PISTOL, LOAD + 1).aim());
        assertFalse(FirearmAnimationMapping.forUse(FirearmKind.PISTOL, LOAD).aim());
    }

    @Test
    void everyMappedNameIsListed() {
        for (FirearmKind kind : FirearmKind.values()) {
            assertTrue(FirearmAnimationMapping.ALL.contains(FirearmAnimationMapping.aimAnimation(kind)));
            assertTrue(FirearmAnimationMapping.ALL.contains(FirearmAnimationMapping.reloadAnimation(kind)));
        }
        assertEquals(4, FirearmAnimationMapping.ALL.stream().distinct().count());
    }

    @Test
    void restartsOnANewAnimationOrANewSessionOnly() {
        Pose aim3 = new Pose(FirearmAnimationMapping.PISTOL_AIM, true, 3);
        Pose aim4 = new Pose(FirearmAnimationMapping.PISTOL_AIM, true, 4);
        Pose aim0 = new Pose(FirearmAnimationMapping.PISTOL_AIM, true, 0);
        Pose reload = new Pose(FirearmAnimationMapping.PISTOL_RELOAD, false, 0);
        assertTrue(FirearmAnimationMapping.restarts(null, aim0));
        assertFalse(FirearmAnimationMapping.restarts(aim3, aim4));
        assertFalse(FirearmAnimationMapping.restarts(aim3, aim3));
        assertTrue(FirearmAnimationMapping.restarts(aim4, aim0), "let go and pressed again between two ticks");
        assertTrue(FirearmAnimationMapping.restarts(aim4, reload));
    }

    @Test
    void reloadIsStretchedToTheReloadTime() {
        // a 60-tick animation over a 60-tick reload plays at normal speed, over 120 ticks at half speed
        assertEquals(1f, FirearmAnimationMapping.speed(false, 60f, 60), 1e-6);
        assertEquals(0.5f, FirearmAnimationMapping.speed(false, 60f, 120), 1e-6);
        assertEquals(2f, FirearmAnimationMapping.speed(false, 100f, 50), 1e-6);
        // the stretched animation ends exactly when loading completes
        float speed = FirearmAnimationMapping.speed(false, 100f, 80);
        assertEquals(100f, 80 * speed, 1e-3);
    }

    @Test
    void aimsAndBadInputsPlayAtNormalSpeedAndExtremesAreClamped() {
        assertEquals(1f, FirearmAnimationMapping.speed(true, 5f, 60), 1e-6);
        assertEquals(1f, FirearmAnimationMapping.speed(false, 0f, 60), 1e-6);
        assertEquals(1f, FirearmAnimationMapping.speed(false, 60f, 0), 1e-6);
        assertEquals(1f, FirearmAnimationMapping.speed(false, Float.NaN, 60), 1e-6);
        assertEquals(FirearmAnimationMapping.MAX_SPEED, FirearmAnimationMapping.speed(false, 100f, 1), 1e-6);
        assertEquals(FirearmAnimationMapping.MIN_SPEED, FirearmAnimationMapping.speed(false, 60f, 1200 * 100), 1e-6);
    }

    @Test
    void startTickCatchesUpOnTheHeldTimeAndStopsAtTheEnd() {
        assertEquals(0f, FirearmAnimationMapping.startTick(0, 0.5f, 60f), 1e-6);
        assertEquals(20f, FirearmAnimationMapping.startTick(40, 0.5f, 60f), 1e-6);
        assertEquals(60f, FirearmAnimationMapping.startTick(500, 0.5f, 60f), 1e-6);
        assertEquals(0f, FirearmAnimationMapping.startTick(-3, 1f, 60f), 1e-6);
    }

    @Test
    void firearmLayerSitsBelowTheMeleeLayer() {
        assertEquals(1400, FirearmAnimationsSetup.priority(1500));
        assertTrue(FirearmAnimationsSetup.priority(1500) < 1500);
        assertEquals(0, FirearmAnimationsSetup.priority(30));
    }
}

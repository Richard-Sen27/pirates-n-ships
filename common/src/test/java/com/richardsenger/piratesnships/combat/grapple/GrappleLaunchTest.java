package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.CrossbowUse;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Held;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.HookUse;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Mode;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.MusketRefusal;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Powder;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Release;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static net.minecraft.world.InteractionHand.MAIN_HAND;
import static net.minecraft.world.InteractionHand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GR3's pure launch rules (docs/design.md §8.3 "Launching"). */
class GrappleLaunchTest {

    private static final double EPS = 1e-9;

    private static InteractionHand launcherHand(Held main, Held off, boolean offhandRequired) {
        return GrappleLaunch.launcherHand(main, off, offhandRequired, true, true);
    }

    // ---- hand arrangement ----

    @Test
    void hookInTheOffHandAndLauncherInTheMainHand() {
        assertEquals(MAIN_HAND, launcherHand(Held.CROSSBOW, Held.HOOK, true));
        assertEquals(MAIN_HAND, launcherHand(Held.MUSKET, Held.HOOK, true));
    }

    @Test
    void swappedHandsNeedOffhandRequiredOff() {
        assertNull(launcherHand(Held.HOOK, Held.CROSSBOW, true), "offhand_required refuses the hook in the main hand");
        assertNull(launcherHand(Held.HOOK, Held.MUSKET, true));
        assertEquals(OFF_HAND, launcherHand(Held.HOOK, Held.CROSSBOW, false));
        assertEquals(OFF_HAND, launcherHand(Held.HOOK, Held.MUSKET, false));
        assertEquals(MAIN_HAND, launcherHand(Held.MUSKET, Held.HOOK, false), "the default arrangement still works");
    }

    @Test
    void noLauncherNoArrangement() {
        for (Held other : new Held[]{Held.EMPTY, Held.OTHER, Held.HOOK}) {
            assertNull(launcherHand(other, Held.HOOK, false));
            assertNull(launcherHand(Held.HOOK, other, false));
        }
        assertNull(launcherHand(Held.CROSSBOW, Held.MUSKET, false), "two launchers and no hook");
    }

    @Test
    void switchedOffLaunchersDoNotCount() {
        assertNull(GrappleLaunch.launcherHand(Held.CROSSBOW, Held.HOOK, true, false, true));
        assertNull(GrappleLaunch.launcherHand(Held.MUSKET, Held.HOOK, true, true, false));
        assertEquals(MAIN_HAND, GrappleLaunch.launcherHand(Held.CROSSBOW, Held.HOOK, true, true, false),
                "the musket toggle does not touch the crossbow");
    }

    @Test
    void theHookIsThrownWithoutALauncherInTheOtherHand() {
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.EMPTY, true, true, true));
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.OTHER, true, true, true));
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(OFF_HAND, Held.OTHER, Held.HOOK, true, true, true));
        // offhand_required: a hook in the main hand with a musket in the off hand is thrown
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.MUSKET, true, true, true));
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(OFF_HAND, Held.CROSSBOW, Held.HOOK, true, false, true),
                "a switched-off crossbow");
    }

    @Test
    void theHookHandsTheUseToTheLauncher() {
        assertEquals(HookUse.LAUNCHER, GrappleLaunch.hookUse(OFF_HAND, Held.CROSSBOW, Held.HOOK, true, true, true));
        assertEquals(HookUse.LAUNCHER, GrappleLaunch.hookUse(OFF_HAND, Held.MUSKET, Held.HOOK, true, true, true));
        assertEquals(HookUse.LAUNCHER, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.MUSKET, false, true, true));
    }

    @Test
    void otherHandIsTheOtherHand() {
        assertEquals(OFF_HAND, GrappleLaunch.other(MAIN_HAND));
        assertEquals(MAIN_HAND, GrappleLaunch.other(OFF_HAND));
    }

    // ---- crossbow ----

    @Test
    void crossbowDrawsWhenEmptyFiresWhenHookedAndLeavesArrowsToVanilla() {
        assertEquals(CrossbowUse.DRAW, GrappleLaunch.crossbowUse(false, false));
        assertEquals(CrossbowUse.FIRE, GrappleLaunch.crossbowUse(true, false));
        assertEquals(CrossbowUse.VANILLA, GrappleLaunch.crossbowUse(false, true));
    }

    @Test
    void drawCompletesAtTheChargeTime() {
        assertFalse(GrappleLaunch.drawn(24, 25));
        assertTrue(GrappleLaunch.drawn(25, 25));
        assertTrue(GrappleLaunch.drawn(0, 0), "quick charge III and beyond");
        assertTrue(GrappleLaunch.drawn(0, -5), "a negative charge time counts as none");
    }

    // ---- musket: load preconditions ----

    @Test
    void powderRules() {
        assertEquals(Powder.CONSUME, GrappleLaunch.powder(false, 3, true));
        assertEquals(Powder.MISSING, GrappleLaunch.powder(false, 0, true));
        assertEquals(Powder.FREE, GrappleLaunch.powder(true, 0, true), "creative");
        assertEquals(Powder.FREE, GrappleLaunch.powder(false, 0, false), "consume_gunpowder off");
    }

    @Test
    void musketLoadRefusals() {
        assertEquals(MusketRefusal.NONE, GrappleLaunch.musketRefusal(false, false, Powder.CONSUME));
        assertEquals(MusketRefusal.NONE, GrappleLaunch.musketRefusal(false, false, Powder.FREE));
        assertEquals(MusketRefusal.LOADED_WITH_SHOT, GrappleLaunch.musketRefusal(true, false, Powder.CONSUME));
        assertEquals(MusketRefusal.LOADED_WITH_SHOT, GrappleLaunch.musketRefusal(true, false, Powder.MISSING),
                "the shot is checked before the powder");
        assertEquals(MusketRefusal.LOADED_WITH_HOOK, GrappleLaunch.musketRefusal(true, true, Powder.CONSUME));
        assertEquals(MusketRefusal.NO_POWDER, GrappleLaunch.musketRefusal(false, false, Powder.MISSING));
    }

    // ---- release ----

    @Test
    void releasingADrawOrLoadBeforeItCompletesCancels() {
        assertEquals(Release.CANCEL, GrappleLaunch.release(Mode.CROSSBOW, false, false, false, 10, 0));
        assertEquals(Release.CANCEL, GrappleLaunch.release(Mode.MUSKET, false, false, false, 10, 0));
        assertEquals(Release.CANCEL, GrappleLaunch.release(Mode.MUSKET, false, false, true, 10, 0), "sneaking changes nothing");
    }

    @Test
    void releasingAfterTheLoadCompletedKeepsTheHookLoaded() {
        assertEquals(Release.KEEP_LOADED, GrappleLaunch.release(Mode.CROSSBOW, false, true, false, 40, 0));
        assertEquals(Release.KEEP_LOADED, GrappleLaunch.release(Mode.MUSKET, false, true, false, 120, 0));
    }

    @Test
    void releasingAnAimedMusketFiresUnlessLowered() {
        assertEquals(Release.FIRE, GrappleLaunch.release(Mode.MUSKET, true, true, false, 0, 0), "a click fires at once");
        assertEquals(Release.FIRE, GrappleLaunch.release(Mode.MUSKET, true, true, false, 30, 0));
        assertEquals(Release.LOWER, GrappleLaunch.release(Mode.MUSKET, true, true, true, 30, 0));
        assertEquals(Release.LOWER, GrappleLaunch.release(Mode.MUSKET, true, true, false, 3, 5), "below aim_min_ticks");
    }

    @Test
    void theCrossbowHasNoAimSession() {
        // a hook-loaded crossbow fires on the press; a later release only ends the held key
        assertEquals(Release.KEEP_LOADED, GrappleLaunch.release(Mode.CROSSBOW, true, true, false, 30, 0));
    }

    @Test
    void aMisfireKeepsTheHook() {
        assertFalse(GrappleLaunch.hookLeaves(true));
        assertTrue(GrappleLaunch.hookLeaves(false));
    }

    // ---- flight ----

    @Test
    void launchSpeedsScaleTheThrow() {
        assertEquals(1.5, GrappleLaunch.speed(Mode.THROW, 1.5, 1.6, 2.4), EPS);
        assertEquals(2.4, GrappleLaunch.speed(Mode.CROSSBOW, 1.5, 1.6, 2.4), EPS);
        assertEquals(3.6, GrappleLaunch.speed(Mode.MUSKET, 1.5, 1.6, 2.4), EPS);
    }

    @Test
    void weaponRopesAreNeverShorterThanTheThrownOne() {
        assertEquals(24, GrappleLaunch.ropeLength(Mode.THROW, 24, 36, 48), EPS);
        assertEquals(36, GrappleLaunch.ropeLength(Mode.CROSSBOW, 24, 36, 48), EPS);
        assertEquals(48, GrappleLaunch.ropeLength(Mode.MUSKET, 24, 36, 48), EPS);
        assertEquals(40, GrappleLaunch.ropeLength(Mode.MUSKET, 40, 36, 20), EPS);
    }

    @Test
    void weaponsAreSteadierThanAThrow() {
        assertTrue(GrappleLaunch.inaccuracy(Mode.CROSSBOW) < GrappleLaunch.inaccuracy(Mode.THROW));
        assertEquals(GrappleLaunch.inaccuracy(Mode.CROSSBOW), GrappleLaunch.inaccuracy(Mode.MUSKET));
    }

    @Test
    void launcherModes() {
        assertEquals(Mode.CROSSBOW, GrappleLaunch.mode(Held.CROSSBOW));
        assertEquals(Mode.MUSKET, GrappleLaunch.mode(Held.MUSKET));
        assertEquals(Mode.THROW, GrappleLaunch.mode(Held.HOOK));
    }
}

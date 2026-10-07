package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Held;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.HookUse;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Mode;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.MusketRefusal;
import com.richardsenger.piratesnships.combat.grapple.GrappleLaunch.Powder;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static net.minecraft.world.InteractionHand.MAIN_HAND;
import static net.minecraft.world.InteractionHand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GR3's pure launch rules with GR4's changes: the musket is the only launcher (docs/design.md §8.3 "Launching"). */
class GrappleLaunchTest {

    private static final double EPS = 1e-9;

    private static InteractionHand launcherHand(Held main, Held off, boolean offhandRequired) {
        return GrappleLaunch.launcherHand(main, off, offhandRequired, true);
    }

    // ---- hand arrangement ----

    @Test
    void hookInTheOffHandAndMusketInTheMainHand() {
        assertEquals(MAIN_HAND, launcherHand(Held.MUSKET, Held.HOOK, true));
    }

    @Test
    void swappedHandsNeedOffhandRequiredOff() {
        assertNull(launcherHand(Held.HOOK, Held.MUSKET, true), "offhand_required refuses the hook in the main hand");
        assertEquals(OFF_HAND, launcherHand(Held.HOOK, Held.MUSKET, false));
        assertEquals(MAIN_HAND, launcherHand(Held.MUSKET, Held.HOOK, false), "the default arrangement still works");
    }

    @Test
    void onlyTheMusketLaunches() {
        for (Held other : new Held[]{Held.EMPTY, Held.OTHER, Held.HOOK}) {
            assertNull(launcherHand(other, Held.HOOK, false), "GR4: " + other + " is no launcher");
            assertNull(launcherHand(Held.HOOK, other, false));
        }
        assertNull(launcherHand(Held.OTHER, Held.MUSKET, false), "a musket and no hook");
    }

    @Test
    void aSwitchedOffMusketDoesNotLaunch() {
        assertNull(GrappleLaunch.launcherHand(Held.MUSKET, Held.HOOK, true, false));
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(OFF_HAND, Held.MUSKET, Held.HOOK, true, false));
    }

    @Test
    void theHookIsThrownWithoutAMusketInTheOtherHand() {
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.EMPTY, true, true));
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.OTHER, true, true));
        // a crossbow or any other item in the main hand: the off-hand hook is a hook (thrown if vanilla offers it the use)
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(OFF_HAND, Held.OTHER, Held.HOOK, true, true));
        // offhand_required: a hook in the main hand with a musket in the off hand is thrown
        assertEquals(HookUse.THROW, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.MUSKET, true, true));
    }

    @Test
    void theHookHandsTheUseToTheMusket() {
        assertEquals(HookUse.LAUNCHER, GrappleLaunch.hookUse(OFF_HAND, Held.MUSKET, Held.HOOK, true, true));
        assertEquals(HookUse.LAUNCHER, GrappleLaunch.hookUse(MAIN_HAND, Held.HOOK, Held.MUSKET, false, true));
    }

    @Test
    void otherHandIsTheOtherHand() {
        assertEquals(OFF_HAND, GrappleLaunch.other(MAIN_HAND));
        assertEquals(MAIN_HAND, GrappleLaunch.other(OFF_HAND));
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

    @Test
    void aMisfireKeepsTheHook() {
        assertFalse(GrappleLaunch.hookLeaves(true));
        assertTrue(GrappleLaunch.hookLeaves(false));
    }

    // ---- flight ----

    @Test
    void theMusketScalesTheThrowSpeed() {
        assertEquals(2.8, GrappleLaunch.speed(Mode.THROW, 2.8, 1.6), EPS);
        assertEquals(4.48, GrappleLaunch.speed(Mode.MUSKET, 2.8, 1.6), EPS);
    }

    @Test
    void theMusketRopeIsNeverShorterThanTheThrownOne() {
        assertEquals(32, GrappleLaunch.ropeLength(Mode.THROW, 32, 64), EPS);
        assertEquals(64, GrappleLaunch.ropeLength(Mode.MUSKET, 32, 64), EPS);
        assertEquals(40, GrappleLaunch.ropeLength(Mode.MUSKET, 40, 20), EPS);
    }

    @Test
    void onlyTheMusketShotFliesFlatter() {
        assertEquals(1.0, GrappleLaunch.gravityFactor(Mode.THROW, 0.5), EPS);
        assertEquals(0.5, GrappleLaunch.gravityFactor(Mode.MUSKET, 0.5), EPS);
        assertEquals(0.0, GrappleLaunch.gravityFactor(Mode.MUSKET, -1.0), EPS, "never negative");
    }

    @Test
    void theMusketIsSteadierThanAThrow() {
        assertTrue(GrappleLaunch.inaccuracy(Mode.MUSKET) < GrappleLaunch.inaccuracy(Mode.THROW));
    }

    @Test
    void launcherModes() {
        assertEquals(Mode.MUSKET, GrappleLaunch.mode(Held.MUSKET));
        assertEquals(Mode.THROW, GrappleLaunch.mode(Held.HOOK));
        assertEquals(Mode.THROW, GrappleLaunch.mode(Held.OTHER));
    }
}

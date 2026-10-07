package com.richardsenger.piratesnships.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The navy soldier's musket action for its animations (M6): goal decision to action, layering, stretch, packing. */
class MusketActionTest {

    private static final MusketRules.Params P = new MusketRules.Params(20, 4, 1.8, 20);

    /** The action the soldier reports for a goal decision, as NavySoldier#customServerAiStep picks it. */
    private static MusketAction action(MusketRules.Decision d, boolean reloading, boolean shoving) {
        return MusketAction.choose(shoving || d.shove(), reloading, d.aim());
    }

    @Test
    void aimingAtATargetInRangeIsAim() {
        MusketRules.Decision d = MusketRules.decide(10, true, true, 5, true, P);
        assertEquals(MusketAction.AIM, action(d, false, false));
        // the firing tick is still the aim (the shot starts the reload afterwards)
        MusketRules.Decision fire = MusketRules.decide(10, true, true, 20, true, P);
        assertEquals(MusketAction.AIM, action(fire, false, false));
    }

    @Test
    void afterTheShotTheReloadShowsWhateverTheGoalDoes() {
        assertEquals(MusketAction.RELOAD, action(MusketRules.decide(10, true, false, 0, true, P), true, false), "holding");
        assertEquals(MusketAction.RELOAD, action(MusketRules.decide(30, true, false, 0, true, P), true, false), "approaching");
        assertEquals(MusketAction.RELOAD, action(MusketRules.decide(3, true, false, 0, true, P), true, false), "backing off");
    }

    @Test
    void aShoveShowsOverAimAndReload() {
        MusketRules.Decision shove = MusketRules.decide(1.5, true, true, 10, true, P);
        assertEquals(MusketAction.SHOVE, action(shove, false, false));
        assertEquals(MusketAction.SHOVE, MusketAction.choose(true, true, false));
        assertEquals(MusketAction.SHOVE, MusketAction.choose(true, false, true));
    }

    @Test
    void noTargetWorkNoAction() {
        assertEquals(MusketAction.NONE, action(MusketRules.decide(30, false, true, 0, true, P), false, false), "approaching, loaded");
        assertEquals(MusketAction.NONE, action(MusketRules.decide(3, true, true, 0, false, P), false, false), "backing off, loaded");
        assertEquals(MusketAction.NONE, MusketAction.choose(false, false, false));
    }

    @Test
    void theArmLayerKeepsItsAnimationUnderAShove() {
        assertEquals(MusketAction.RELOAD, MusketAction.held(MusketAction.SHOVE, MusketAction.RELOAD));
        assertEquals(MusketAction.AIM, MusketAction.held(MusketAction.SHOVE, MusketAction.AIM));
        assertEquals(MusketAction.NONE, MusketAction.held(MusketAction.SHOVE, MusketAction.NONE));
        assertEquals(MusketAction.NONE, MusketAction.held(MusketAction.SHOVE, MusketAction.SHOVE));
        assertEquals(MusketAction.AIM, MusketAction.held(MusketAction.AIM, MusketAction.RELOAD));
        assertEquals(MusketAction.NONE, MusketAction.held(MusketAction.NONE, MusketAction.RELOAD));
    }

    @Test
    void theReloadIsStretchedToTheReloadTime() {
        assertEquals(1.0, MusketAction.reloadSpeed(100), 1e-9);
        assertEquals(2.0, MusketAction.reloadSpeed(50), 1e-9);
        assertEquals(0.5, MusketAction.reloadSpeed(200), 1e-9);
        assertEquals(1.0, MusketAction.reloadSpeed(0), 1e-9);
        // 5 s of animation at that speed take exactly the reload ticks
        for (int ticks : new int[]{1, 37, 100, 160, 1200}) {
            assertEquals(ticks, MusketAction.RELOAD_ANIMATION_TICKS / MusketAction.reloadSpeed(ticks), 1e-6);
        }
    }

    @Test
    void packRoundTrips() {
        for (MusketAction a : MusketAction.values()) {
            for (int ticks : new int[]{0, 1, 100, 1200, 65535}) {
                int packed = MusketAction.pack(a, ticks);
                assertEquals(a, MusketAction.unpackAction(packed));
                assertEquals(ticks, MusketAction.unpackReloadTicks(packed));
            }
        }
        assertEquals(65535, MusketAction.unpackReloadTicks(MusketAction.pack(MusketAction.RELOAD, 1 << 20)), "clamped");
        assertEquals(0, MusketAction.unpackReloadTicks(MusketAction.pack(MusketAction.RELOAD, -5)), "clamped");
    }
}

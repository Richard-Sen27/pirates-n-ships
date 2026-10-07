package com.richardsenger.piratesnships.mob.kraken;

import com.richardsenger.piratesnships.mob.kraken.KrakenBrain.Input;
import com.richardsenger.piratesnships.mob.kraken.KrakenBrain.Params;
import com.richardsenger.piratesnships.mob.kraken.KrakenBrain.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KrakenBrainTest {

    private static final Params P = new Params(false, 0.3, 1200);
    private static final Input NONE = new Input(1.0, false, false, false);
    private static final Input FAR = new Input(1.0, true, false, false);
    private static final Input NEAR = new Input(1.0, true, true, false);
    private static final Input AT_SPOT = new Input(1.0, true, true, true);

    private static KrakenBrain attacking() {
        KrakenBrain b = new KrakenBrain();
        b.tick(NEAR, P);
        b.tick(AT_SPOT, P);
        assertEquals(State.ATTACK, b.state());
        return b;
    }

    @Test
    void lurksWithoutATargetAndWhileTheTargetIsFar() {
        KrakenBrain b = new KrakenBrain();
        for (int i = 0; i < 100; i++) b.tick(NONE, P);
        assertEquals(State.LURK, b.state());
        for (int i = 0; i < 100; i++) b.tick(FAR, P);
        assertEquals(State.LURK, b.state());
    }

    @Test
    void surfacesWhenTheTargetIsNearAndAttacksAtTheSpot() {
        KrakenBrain b = new KrakenBrain();
        assertEquals(State.SURFACE, b.tick(NEAR, P));
        assertEquals(State.SURFACE, b.tick(NEAR, P));
        assertEquals(State.ATTACK, b.tick(AT_SPOT, P));
    }

    @Test
    void surfacingGivesUpWhenTheTargetIsLostOrTheSpotIsNotReached() {
        KrakenBrain b = new KrakenBrain();
        b.tick(NEAR, P);
        for (int i = 0; i < KrakenBrain.LOSE_TARGET_TICKS - 1; i++) b.tick(NONE, P);
        assertEquals(State.SURFACE, b.state());
        b.tick(NONE, P);
        assertEquals(State.LURK, b.state());

        KrakenBrain c = new KrakenBrain();
        c.tick(NEAR, P);
        for (int i = 0; i < KrakenBrain.SURFACE_TIMEOUT_TICKS; i++) c.tick(NEAR, P);
        assertEquals(State.LURK, c.state());
    }

    @Test
    void attackEndsInLurkWhenTheTargetIsGoneForAWhile() {
        KrakenBrain b = attacking();
        for (int i = 0; i < KrakenBrain.LOSE_TARGET_TICKS - 1; i++) b.tick(NONE, P);
        assertEquals(State.ATTACK, b.state());
        b.tick(NEAR, P); // back in time: the count starts over
        for (int i = 0; i < KrakenBrain.LOSE_TARGET_TICKS - 1; i++) b.tick(NONE, P);
        assertEquals(State.ATTACK, b.state());
        b.tick(NONE, P);
        assertEquals(State.LURK, b.state());
    }

    @Test
    void retreatsAfterTheAttackDurationCountedOverAllAttacks() {
        Params p = new Params(false, 0.3, 200);
        KrakenBrain b = new KrakenBrain();
        b.tick(NEAR, p);
        b.tick(AT_SPOT, p);
        for (int i = 0; i < 60; i++) b.tick(AT_SPOT, p);
        for (int i = 0; i < KrakenBrain.LOSE_TARGET_TICKS; i++) b.tick(NONE, p);
        assertEquals(State.LURK, b.state());
        int before = b.attackTicks();
        b.tick(NEAR, p);
        b.tick(AT_SPOT, p);
        while (b.state() == State.ATTACK) b.tick(AT_SPOT, p);
        assertEquals(State.RETREAT, b.state());
        assertEquals(200, b.attackTicks());
        assertTrue(before > 60, "the first attack counted: " + before);
    }

    @Test
    void retreatsBelowTheHealthFractionFromAnyStateAndForGood() {
        for (State from : new State[] {State.LURK, State.SURFACE, State.ATTACK}) {
            KrakenBrain b = new KrakenBrain();
            if (from != State.LURK) b.tick(NEAR, P);
            if (from == State.ATTACK) b.tick(AT_SPOT, P);
            assertEquals(from, b.state());
            assertEquals(State.RETREAT, b.tick(new Input(0.29, true, true, true), P));
            for (int i = 0; i < 50; i++) b.tick(new Input(1.0, true, true, true), P); // healed: still retreating
            assertEquals(State.RETREAT, b.state());
        }
        KrakenBrain b = attacking();
        assertEquals(State.ATTACK, b.tick(new Input(0.31, true, true, true), P));
    }

    @Test
    void retreatFinishesAfterItsTicks() {
        KrakenBrain b = new KrakenBrain();
        b.tick(new Input(0.1, false, false, false), P);
        for (int i = 0; i < KrakenBrain.RETREAT_TICKS; i++) {
            assertFalse(b.finished());
            b.tick(NONE, P);
        }
        assertTrue(b.finished());
    }

    @Test
    void zeroRetreatFractionFightsToTheDeath() {
        Params p = new Params(false, 0.0, 1200);
        KrakenBrain b = new KrakenBrain();
        b.tick(NEAR, p);
        b.tick(AT_SPOT, p);
        assertEquals(State.ATTACK, b.tick(new Input(0.01, true, true, true), p));
    }

    @Test
    void peacefulNeverLeavesTheDeep() {
        Params p = new Params(true, 0.3, 1200);
        KrakenBrain b = new KrakenBrain();
        for (int i = 0; i < 50; i++) b.tick(AT_SPOT, p);
        assertEquals(State.LURK, b.state());
        KrakenBrain a = attacking();
        assertEquals(State.LURK, a.tick(AT_SPOT, p));
    }

    @Test
    void loadRestoresAndFallsBackToLurk() {
        KrakenBrain b = new KrakenBrain();
        b.load("ATTACK", 12, 300);
        assertEquals(State.ATTACK, b.state());
        assertEquals(12, b.ticksInState());
        assertEquals(300, b.attackTicks());
        b.load("NONSENSE", 1, 1);
        assertEquals(State.LURK, b.state());
    }
}

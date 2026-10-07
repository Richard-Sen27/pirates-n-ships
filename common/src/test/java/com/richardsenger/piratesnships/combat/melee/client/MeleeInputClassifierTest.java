package com.richardsenger.piratesnships.combat.melee.client;

import com.richardsenger.piratesnships.combat.melee.net.MeleeAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeleeInputClassifierTest {

    static final MeleeInputClassifier.Thresholds T = MeleeInputClassifier.Thresholds.DEFAULTS;

    /** Holds attack for {@code ticks} ticks, then releases; returns everything emitted. */
    static List<MeleeAction> attack(MeleeInputClassifier c, int ticks, MeleeInputClassifier.Thresholds t) {
        List<MeleeAction> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) out.addAll(c.tick(true, false, t));
        out.addAll(c.tick(false, false, t));
        return out;
    }

    static List<MeleeAction> use(MeleeInputClassifier c, int ticks, MeleeInputClassifier.Thresholds t) {
        List<MeleeAction> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) out.addAll(c.tick(false, true, t));
        out.addAll(c.tick(false, false, t));
        return out;
    }

    @Test
    void defaultsMatchTheSpec() {
        assertEquals(6, T.holdToThrustTicks());
        assertEquals(4, T.parryTapTicks());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5})
    void shortAttackIsASlashOnRelease(int ticks) {
        MeleeInputClassifier c = new MeleeInputClassifier();
        for (int i = 0; i < ticks; i++) assertEquals(List.of(), c.tick(true, false, T), "nothing while held");
        assertEquals(List.of(MeleeAction.SLASH), c.tick(false, false, T));
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 40})
    void longAttackIsAThrustOnRelease(int ticks) {
        assertEquals(List.of(MeleeAction.THRUST), attack(new MeleeInputClassifier(), ticks, T));
    }

    @Test
    void thrustThresholdComesFromTheParams() {
        var t = new MeleeInputClassifier.Thresholds(3, 4);
        assertEquals(List.of(MeleeAction.SLASH), attack(new MeleeInputClassifier(), 2, t));
        assertEquals(List.of(MeleeAction.THRUST), attack(new MeleeInputClassifier(), 3, t));
    }

    @Test
    void useRaisesTheGuardAtOnceAndLowersItOnRelease() {
        MeleeInputClassifier c = new MeleeInputClassifier();
        assertEquals(List.of(MeleeAction.GUARD_DOWN), c.tick(false, true, T));
        assertTrue(c.guarding());
        for (int i = 0; i < 10; i++) assertEquals(List.of(), c.tick(false, true, T));
        assertEquals(List.of(MeleeAction.GUARD_UP), c.tick(false, false, T));
        assertFalse(c.guarding());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void useTapIsAParryAfterTheGuardDrops(int ticks) {
        assertEquals(List.of(MeleeAction.GUARD_DOWN, MeleeAction.GUARD_UP, MeleeAction.PARRY), use(new MeleeInputClassifier(), ticks, T));
    }

    @Test
    void useHeldForTheTapThresholdIsNoParry() {
        assertEquals(List.of(MeleeAction.GUARD_DOWN, MeleeAction.GUARD_UP), use(new MeleeInputClassifier(), 4, T));
        var t = new MeleeInputClassifier.Thresholds(6, 8);
        assertEquals(List.of(MeleeAction.GUARD_DOWN, MeleeAction.GUARD_UP, MeleeAction.PARRY), use(new MeleeInputClassifier(), 7, t));
    }

    @Test
    void keysAreIndependent() {
        MeleeInputClassifier c = new MeleeInputClassifier();
        assertEquals(List.of(MeleeAction.GUARD_DOWN), c.tick(false, true, T));
        for (int i = 0; i < 6; i++) c.tick(true, true, T);
        assertEquals(List.of(MeleeAction.THRUST), c.tick(false, true, T), "a thrust out of the guard");
        assertEquals(List.of(MeleeAction.GUARD_UP), c.tick(false, false, T));
    }

    @Test
    void releaseOfBothInOneTickSendsTheAttackFirst() {
        MeleeInputClassifier c = new MeleeInputClassifier();
        c.tick(true, true, T);
        assertEquals(List.of(MeleeAction.SLASH, MeleeAction.GUARD_UP, MeleeAction.PARRY), c.tick(false, false, T));
    }

    @Test
    void resetDropsAHeldAttackAndLowersARaisedGuard() {
        MeleeInputClassifier c = new MeleeInputClassifier();
        c.tick(true, true, T);
        assertEquals(List.of(MeleeAction.GUARD_UP), c.reset());
        assertEquals(0, c.attackHeldTicks());
        assertEquals(List.of(), c.tick(false, false, T), "no slash after a reset");
        assertEquals(List.of(), c.reset(), "nothing to lower");
    }

    @Test
    void attackHeldTicksCountsUp() {
        MeleeInputClassifier c = new MeleeInputClassifier();
        c.tick(true, false, T);
        c.tick(true, false, T);
        assertEquals(2, c.attackHeldTicks());
    }

    @Test
    void thresholdsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MeleeInputClassifier.Thresholds(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new MeleeInputClassifier.Thresholds(6, 0));
    }
}

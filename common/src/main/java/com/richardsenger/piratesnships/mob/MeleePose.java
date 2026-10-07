package com.richardsenger.piratesnships.mob;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import org.jetbrains.annotations.Nullable;

/**
 * The part of a mob's melee state its model needs for the telegraph (wind-up, swing, guard, parry, stagger), packed
 * into one synced int: phase (bits 0-3), attack (4-5: 0 none, 1 slash, 2 thrust), riposte (6), feint (7: the recovery
 * is a feint recovery), duration (8-23). It changes only when the phase changes, so it is sent once per phase. Pure.
 */
public record MeleePose(Phase phase, @Nullable AttackKind attack, boolean riposte, int duration, boolean feint) {

    public static final MeleePose IDLE = new MeleePose(Phase.IDLE, null, false, 0);

    /** Not a feint. */
    public MeleePose(Phase phase, @Nullable AttackKind attack, boolean riposte, int duration) {
        this(phase, attack, riposte, duration, false);
    }
    private static final Phase[] PHASES = Phase.values();
    private static final AttackKind[] ATTACKS = AttackKind.values();

    public static MeleePose of(CombatState s) {
        boolean attacking = s.phase().attacking();
        return new MeleePose(s.phase(), attacking ? s.attack() : null, attacking && s.riposteAttack(), Math.max(0, s.duration()), s.feint());
    }

    public int pack() {
        int a = attack == null ? 0 : attack.ordinal() + 1;
        return phase.ordinal() | a << 4 | (riposte ? 1 << 6 : 0) | (feint ? 1 << 7 : 0) | Math.min(duration, 0xFFFF) << 8;
    }

    public static MeleePose unpack(int v) {
        int p = v & 0xF;
        int a = v >> 4 & 0x3;
        Phase phase = p < PHASES.length ? PHASES[p] : Phase.IDLE;
        AttackKind attack = a == 0 || a > ATTACKS.length ? null : ATTACKS[a - 1];
        return new MeleePose(phase, attack, (v & 1 << 6) != 0, v >>> 8 & 0xFFFF, (v & 1 << 7) != 0);
    }
}

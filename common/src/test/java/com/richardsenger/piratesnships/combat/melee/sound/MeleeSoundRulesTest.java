package com.richardsenger.piratesnships.combat.melee.sound;

import com.richardsenger.piratesnships.combat.melee.resolve.HitResult.Outcome;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.At;
import com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Cue;
import com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Params;
import com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Sound;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeleeSoundRulesTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;
    static final WeaponDefinition W = DefaultWeapons.SABER;
    static final Params ON = Params.DEFAULTS;
    static final Params OFF = new Params(false, 1.0);

    static CombatState ok(InputResult r) {
        assertTrue(r.accepted(), () -> "refused: " + r.refusal());
        return r.state();
    }

    /** Runs an attack tick by tick and collects every cue of its state changes. */
    static List<Cue> attackCues(AttackKind kind, CombatState start, Params params) {
        List<Cue> cues = new ArrayList<>();
        CombatState s = ok(CombatRules.startAttack(start, kind, W, P));
        cues.addAll(MeleeSoundRules.stateChange(start, s, params));
        for (int i = 0; i < 40; i++) {
            CombatState n = CombatRules.tick(s, W, P);
            cues.addAll(MeleeSoundRules.stateChange(s, n, params));
            s = n;
        }
        assertEquals(Phase.IDLE, s.phase(), "the attack finished");
        return cues;
    }

    @ParameterizedTest
    @EnumSource(AttackKind.class)
    void anAttackSwingsOnceWhenItsHitFramesBegin(AttackKind kind) {
        List<Cue> cues = attackCues(kind, CombatState.fresh(P.staminaMax()), ON);
        assertEquals(1, cues.size(), () -> "cues " + cues);
        Cue c = cues.get(0);
        assertEquals(Sound.SWING, c.sound());
        assertEquals(At.SELF, c.at());
        assertEquals(kind == AttackKind.THRUST ? MeleeSoundRules.SWING_THRUST : MeleeSoundRules.SWING_SLASH, c);
    }

    @Test
    void theSwingStartsExactlyOnEnteringTheHitFrames() {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        while (s.phase() == Phase.WINDUP) {
            CombatState n = CombatRules.tick(s, W, P);
            List<Cue> cues = MeleeSoundRules.stateChange(s, n, ON);
            assertEquals(n.phase() == Phase.ACTIVE ? 1 : 0, cues.size(), () -> "at " + n);
            s = n;
        }
        assertEquals(Phase.ACTIVE, s.phase());
    }

    @Test
    void aThrustSwingsHigherThanASlash() {
        Cue slash = MeleeSoundRules.SWING_SLASH, thrust = MeleeSoundRules.SWING_THRUST;
        assertTrue(thrust.minPitch() >= slash.maxPitch(), "thrust pitch range lies above the slash's");
        assertEquals(slash.minVolume(), thrust.minVolume());
        assertEquals(slash.maxVolume(), thrust.maxVolume());
    }

    @Test
    void aRiposteSwingsLikeANormalAttack() {
        CombatState parried = CombatRules.parrySucceeded(ok(CombatRules.parry(CombatState.fresh(P.staminaMax()), W, P)), P);
        assertTrue(parried.riposteReady());
        assertEquals(List.of(MeleeSoundRules.SWING_SLASH), attackCues(AttackKind.SLASH, parried, ON));
    }

    @Test
    void aFeintWhooshesQuietlyAndHighInsteadOfSwinging() {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        CombatState f = ok(CombatRules.feint(s, P));
        assertEquals(List.of(MeleeSoundRules.FEINT), MeleeSoundRules.stateChange(s, f, ON));
        Cue feint = MeleeSoundRules.FEINT;
        assertEquals(Sound.SWING, feint.sound());
        assertTrue(feint.maxVolume() < MeleeSoundRules.SWING_SLASH.minVolume(), "quieter than a swing");
        assertTrue(feint.minPitch() > MeleeSoundRules.SWING_THRUST.maxPitch(), "higher than any swing");
        for (int i = 0; i < 20; i++) {
            CombatState n = CombatRules.tick(f, W, P);
            assertEquals(List.of(), MeleeSoundRules.stateChange(f, n, ON), "the feint recovery runs out silently");
            f = n;
        }
    }

    @Test
    void guardAndParryInputsAreSilent() {
        CombatState fresh = CombatState.fresh(P.staminaMax());
        assertEquals(List.of(), MeleeSoundRules.stateChange(fresh, ok(CombatRules.guardDown(fresh, W, P)), ON));
        assertEquals(List.of(), MeleeSoundRules.stateChange(fresh, ok(CombatRules.parry(fresh, W, P)), ON));
        assertEquals(List.of(), MeleeSoundRules.stateChange(fresh, fresh, ON));
    }

    @Test
    void aSwordHitRingsOnArmourAndThudsOnFlesh() {
        assertEquals(List.of(MeleeSoundRules.HIT_FLESH), MeleeSoundRules.hit(Outcome.HIT, false, true, false, ON));
        assertEquals(List.of(MeleeSoundRules.HIT_ARMOR), MeleeSoundRules.hit(Outcome.HIT, false, true, true, ON));
        assertEquals(Sound.HIT_FLESH, MeleeSoundRules.HIT_FLESH.sound());
        assertEquals(Sound.HIT_ARMOR, MeleeSoundRules.HIT_ARMOR.sound());
        assertEquals(At.DEFENDER, MeleeSoundRules.HIT_FLESH.at());
        assertEquals(At.DEFENDER, MeleeSoundRules.HIT_ARMOR.at());
    }

    @Test
    void aStaggeringHitAddsALowThudAtTheDefender() {
        assertEquals(List.of(MeleeSoundRules.HIT_FLESH, MeleeSoundRules.STAGGER), MeleeSoundRules.hit(Outcome.HIT, true, true, false, ON));
        Cue st = MeleeSoundRules.STAGGER;
        assertEquals(Sound.HIT_FLESH, st.sound());
        assertEquals(At.DEFENDER, st.at());
        assertTrue(st.maxPitch() < MeleeSoundRules.HIT_FLESH.minPitch(), "lower than a hit");
        assertTrue(st.maxVolume() < MeleeSoundRules.HIT_FLESH.minVolume(), "quieter than a hit");
    }

    @Test
    void aParryClashesLoudAtTheDefenderWithoutAStaggerCue() {
        assertEquals(List.of(MeleeSoundRules.PARRY), MeleeSoundRules.hit(Outcome.PARRIED, false, true, true, ON));
        assertEquals(List.of(MeleeSoundRules.PARRY), MeleeSoundRules.hit(Outcome.PARRIED, false, false, false, ON));
        Cue p = MeleeSoundRules.PARRY;
        assertEquals(Sound.PARRY, p.sound());
        assertEquals(At.DEFENDER, p.at());
        assertEquals(1.0f, p.minVolume());
        assertEquals(0.9f, p.minPitch());
        assertEquals(1.1f, p.maxPitch());
    }

    @Test
    void aGuardClashesLowAndQuiet() {
        assertEquals(List.of(MeleeSoundRules.GUARD), MeleeSoundRules.hit(Outcome.GUARDED, false, true, false, ON));
        assertEquals(List.of(MeleeSoundRules.GUARD, MeleeSoundRules.STAGGER), MeleeSoundRules.hit(Outcome.GUARD_BROKEN, true, true, false, ON));
        Cue g = MeleeSoundRules.GUARD;
        assertEquals(Sound.PARRY, g.sound());
        assertEquals(0.7f, g.minPitch());
        assertEquals(0.8f, g.maxPitch());
        assertTrue(g.maxVolume() < MeleeSoundRules.PARRY.minVolume(), "quieter than a parry");
    }

    @Test
    void vanillaHitsOnlySoundWhenParriedGuardedOrStaggering() {
        assertEquals(List.of(), MeleeSoundRules.hit(Outcome.HIT, false, false, true, ON));
        assertEquals(List.of(MeleeSoundRules.STAGGER), MeleeSoundRules.hit(Outcome.HIT, true, false, false, ON));
        assertEquals(List.of(MeleeSoundRules.GUARD), MeleeSoundRules.hit(Outcome.GUARDED, false, false, false, ON));
        assertEquals(List.of(), MeleeSoundRules.hit(Outcome.UNAFFECTED, false, false, false, ON));
        assertEquals(List.of(), MeleeSoundRules.hit(Outcome.UNAFFECTED, true, true, true, ON));
    }

    @Test
    void disabledPlaysNothing() {
        assertEquals(List.of(), attackCues(AttackKind.SLASH, CombatState.fresh(P.staminaMax()), OFF));
        assertEquals(List.of(), attackCues(AttackKind.THRUST, CombatState.fresh(P.staminaMax()), OFF));
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        assertEquals(List.of(), MeleeSoundRules.stateChange(s, ok(CombatRules.feint(s, P)), OFF));
        for (Outcome o : Outcome.values()) {
            assertEquals(List.of(), MeleeSoundRules.hit(o, true, true, true, OFF), o::name);
        }
        assertEquals(List.of(), MeleeSoundRules.unsheathe(OFF));
        assertEquals(List.of(MeleeSoundRules.UNSHEATHE), MeleeSoundRules.unsheathe(ON));
    }

    @Test
    void volumeAndPitchStayInTheirRangesAndVolumeScales() {
        for (Cue c : List.of(MeleeSoundRules.SWING_SLASH, MeleeSoundRules.SWING_THRUST, MeleeSoundRules.FEINT,
                MeleeSoundRules.HIT_FLESH, MeleeSoundRules.HIT_ARMOR, MeleeSoundRules.PARRY, MeleeSoundRules.GUARD,
                MeleeSoundRules.STAGGER, MeleeSoundRules.UNSHEATHE)) {
            assertTrue(c.minVolume() <= c.maxVolume() && c.minPitch() <= c.maxPitch(), c::toString);
            assertTrue(c.minPitch() >= 0.5f && c.maxPitch() <= 2.0f, () -> "vanilla clamps pitch to 0.5..2: " + c);
            for (double u : new double[]{0.0, 0.25, 0.5, 0.999}) {
                assertTrue(c.volume(u, 1.0) >= c.minVolume() - 1e-6 && c.volume(u, 1.0) <= c.maxVolume() + 1e-6, c::toString);
                assertTrue(c.pitch(u) >= c.minPitch() - 1e-6 && c.pitch(u) <= c.maxPitch() + 1e-6, c::toString);
                assertEquals(c.volume(u, 1.0) * 0.5f, c.volume(u, 0.5), 1e-6);
                assertEquals(c.volume(u, 1.0) * 2.0f, c.volume(u, 2.0), 1e-6);
                assertEquals(0f, c.volume(u, 0.0));
            }
        }
        assertEquals(0.7f, MeleeSoundRules.SWING_SLASH.volume(0.0, 1.0), 1e-6);
        assertEquals(0.9f, MeleeSoundRules.SWING_SLASH.volume(1.0, 1.0), 1e-6);
        assertEquals(1.3f, MeleeSoundRules.SWING_THRUST.pitch(1.0), 1e-6);
    }
}

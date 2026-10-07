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
import com.richardsenger.piratesnships.combat.melee.sound.MeleeSoundRules.Hit;
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

    /**
     * Runs an attack tick by tick and collects every cue of its state changes; on tick {@code hitAt} (counted from the
     * attack start, -1 = never) the attack hits a target as the engine's sweep would.
     */
    static List<Cue> attackCues(AttackKind kind, CombatState start, Params params, int hitAt) {
        List<Cue> cues = new ArrayList<>();
        CombatState s = ok(CombatRules.startAttack(start, kind, W, P));
        cues.addAll(MeleeSoundRules.stateChange(start, s, params));
        for (int i = 0; i < 40; i++) {
            if (i == hitAt) {
                assertEquals(Phase.ACTIVE, s.phase(), "the hit lands in the hit frames");
                CombatState n = s.withHitTarget(42);
                cues.addAll(MeleeSoundRules.stateChange(s, n, params));
                s = n;
            }
            CombatState n = CombatRules.tick(s, W, P);
            cues.addAll(MeleeSoundRules.stateChange(s, n, params));
            s = n;
        }
        assertEquals(Phase.IDLE, s.phase(), "the attack finished");
        return cues;
    }

    static int firstActiveTick(AttackKind kind) {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), kind, W, P));
        int i = 0;
        while (s.phase() != Phase.ACTIVE) {
            s = CombatRules.tick(s, W, P);
            i++;
        }
        return i;
    }

    static Hit swordHit(Outcome o, boolean heavy, boolean blade, boolean armoured) {
        return new Hit(o, true, heavy, blade, armoured);
    }

    @ParameterizedTest
    @EnumSource(AttackKind.class)
    void anAttackIntoTheAirWhooshesOnceWhenItsHitFramesEnd(AttackKind kind) {
        List<Cue> cues = attackCues(kind, CombatState.fresh(P.staminaMax()), ON, -1);
        assertEquals(List.of(kind == AttackKind.THRUST ? MeleeSoundRules.MISS_THRUST : MeleeSoundRules.MISS_SLASH), cues);
        assertEquals(Sound.MISS, cues.get(0).sound());
        assertEquals(At.SELF, cues.get(0).at());
    }

    @ParameterizedTest
    @EnumSource(AttackKind.class)
    void anAttackThatHitsHasNoCueOfItsOwnStateChanges(AttackKind kind) {
        assertEquals(List.of(), attackCues(kind, CombatState.fresh(P.staminaMax()), ON, firstActiveTick(kind)));
    }

    @Test
    void theMissSoundsExactlyOnLeavingTheHitFramesForTheRecovery() {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        int misses = 0;
        while (s.phase() != Phase.IDLE) {
            CombatState n = CombatRules.tick(s, W, P);
            boolean miss = s.phase() == Phase.ACTIVE && n.phase() == Phase.RECOVERY;
            CombatState from = s;
            assertEquals(miss ? 1 : 0, MeleeSoundRules.stateChange(s, n, ON).size(), () -> from + " -> " + n);
            if (miss) misses++;
            s = n;
        }
        assertEquals(1, misses);
    }

    @Test
    void aParriedAttackerStaggeringOutOfTheHitFramesIsSilent() {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        while (s.phase() != Phase.ACTIVE) s = CombatRules.tick(s, W, P);
        assertEquals(List.of(), MeleeSoundRules.stateChange(s, CombatRules.stagger(s, P.parryStaggerTicks()), ON));
    }

    @Test
    void aThrustMissIsLowerThanASlashMiss() {
        Cue slash = MeleeSoundRules.MISS_SLASH, thrust = MeleeSoundRules.MISS_THRUST;
        assertTrue(thrust.maxPitch() < slash.minPitch(), "thrust whoosh lies below the slash's");
        assertEquals(slash.minVolume(), thrust.minVolume());
        assertEquals(slash.maxVolume(), thrust.maxVolume());
    }

    @Test
    void aRiposteIntoTheAirWhooshesLikeANormalAttack() {
        CombatState parried = CombatRules.parrySucceeded(ok(CombatRules.parry(CombatState.fresh(P.staminaMax()), W, P)), P);
        assertTrue(parried.riposteReady());
        assertEquals(List.of(MeleeSoundRules.MISS_SLASH), attackCues(AttackKind.SLASH, parried, ON, -1));
    }

    @Test
    void aFeintIsSilent() {
        CombatState s = ok(CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, W, P));
        CombatState f = ok(CombatRules.feint(s, P));
        assertEquals(List.of(), MeleeSoundRules.stateChange(s, f, ON));
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
    void aSlashOnFleshHitsLightAndAHeavyHitHitsHeavy() {
        assertEquals(List.of(MeleeSoundRules.HIT_FLESH), MeleeSoundRules.hit(swordHit(Outcome.HIT, false, false, false), ON));
        assertEquals(List.of(MeleeSoundRules.HIT_HEAVY), MeleeSoundRules.hit(swordHit(Outcome.HIT, true, false, false), ON));
        assertEquals(Sound.HIT, MeleeSoundRules.HIT_FLESH.sound());
        assertEquals(Sound.HIT_HEAVY, MeleeSoundRules.HIT_HEAVY.sound());
        assertEquals(At.DEFENDER, MeleeSoundRules.HIT_FLESH.at());
        assertEquals(At.DEFENDER, MeleeSoundRules.HIT_HEAVY.at());
    }

    @Test
    void heavyMeansThrustRiposteOrStagger() {
        assertFalse(MeleeSoundRules.heavy(AttackKind.SLASH, false, false));
        assertTrue(MeleeSoundRules.heavy(AttackKind.THRUST, false, false));
        assertTrue(MeleeSoundRules.heavy(AttackKind.SLASH, true, false));
        assertTrue(MeleeSoundRules.heavy(AttackKind.SLASH, false, true));
        assertFalse(MeleeSoundRules.heavy(null, false, false));
    }

    @Test
    void aChestplateRingsWhateverTheWeight() {
        assertEquals(List.of(MeleeSoundRules.HIT_ARMOR), MeleeSoundRules.hit(swordHit(Outcome.HIT, false, false, true), ON));
        assertEquals(List.of(MeleeSoundRules.HIT_ARMOR), MeleeSoundRules.hit(swordHit(Outcome.HIT, true, false, true), ON));
        assertEquals(Sound.HIT_ARMOR, MeleeSoundRules.HIT_ARMOR.sound());
        assertEquals(At.DEFENDER, MeleeSoundRules.HIT_ARMOR.at());
    }

    @Test
    void bladeOnBladeClashesInsteadOfHitting() {
        assertEquals(List.of(MeleeSoundRules.BLADE_CLASH), MeleeSoundRules.hit(swordHit(Outcome.HIT, false, true, false), ON));
        assertEquals(List.of(MeleeSoundRules.BLADE_CLASH), MeleeSoundRules.hit(swordHit(Outcome.HIT, true, true, true), ON));
        assertEquals(Sound.CLASH, MeleeSoundRules.BLADE_CLASH.sound());
        assertEquals(At.DEFENDER, MeleeSoundRules.BLADE_CLASH.at());
        assertTrue(MeleeSoundRules.BLADE_CLASH.maxVolume() <= MeleeSoundRules.PARRY.minVolume(), "not louder than a parry");
        assertTrue(MeleeSoundRules.BLADE_CLASH.minVolume() > MeleeSoundRules.GUARD.maxVolume(), "louder than a guard");
    }

    @Test
    void bladeOnBladeNeedsADefenderMidAttackWithASword() {
        CombatState fresh = CombatState.fresh(P.staminaMax());
        CombatState windup = ok(CombatRules.startAttack(fresh, AttackKind.SLASH, W, P));
        CombatState active = windup;
        while (active.phase() != Phase.ACTIVE) active = CombatRules.tick(active, W, P);
        CombatState recovery = active;
        while (recovery.phase() != Phase.RECOVERY) recovery = CombatRules.tick(recovery, W, P);
        assertTrue(MeleeSoundRules.bladeOnBlade(windup, true));
        assertTrue(MeleeSoundRules.bladeOnBlade(active, true));
        assertFalse(MeleeSoundRules.bladeOnBlade(windup, false), "an unarmed attacker has no blade");
        assertFalse(MeleeSoundRules.bladeOnBlade(recovery, true), "a recovering defender is open");
        assertFalse(MeleeSoundRules.bladeOnBlade(fresh, true));
        assertFalse(MeleeSoundRules.bladeOnBlade(ok(CombatRules.guardDown(fresh, W, P)), true), "a guard is its own outcome");
        assertFalse(MeleeSoundRules.bladeOnBlade(null, true));
    }

    @Test
    void aParryClashesLoudAtTheDefender() {
        assertEquals(List.of(MeleeSoundRules.PARRY), MeleeSoundRules.hit(swordHit(Outcome.PARRIED, false, false, true), ON));
        assertEquals(List.of(MeleeSoundRules.PARRY), MeleeSoundRules.hit(new Hit(Outcome.PARRIED, false, false, false, false), ON));
        Cue p = MeleeSoundRules.PARRY;
        assertEquals(Sound.CLASH, p.sound());
        assertEquals(At.DEFENDER, p.at());
        assertEquals(1.0f, p.minVolume());
    }

    @Test
    void aGuardClashesLowAndQuietAlsoWhenItBreaks() {
        assertEquals(List.of(MeleeSoundRules.GUARD), MeleeSoundRules.hit(swordHit(Outcome.GUARDED, false, false, false), ON));
        assertEquals(List.of(MeleeSoundRules.GUARD), MeleeSoundRules.hit(swordHit(Outcome.GUARD_BROKEN, true, false, false), ON));
        Cue g = MeleeSoundRules.GUARD;
        assertEquals(Sound.CLASH, g.sound());
        assertTrue(g.maxPitch() < MeleeSoundRules.PARRY.minPitch(), "lower than a parry");
        assertTrue(g.maxVolume() < MeleeSoundRules.PARRY.minVolume(), "quieter than a parry");
    }

    @Test
    void vanillaHitsOnlySoundWhenParriedOrGuarded() {
        assertEquals(List.of(), MeleeSoundRules.hit(new Hit(Outcome.HIT, false, false, false, true), ON));
        assertEquals(List.of(), MeleeSoundRules.hit(new Hit(Outcome.HIT, false, true, true, false), ON));
        assertEquals(List.of(MeleeSoundRules.GUARD), MeleeSoundRules.hit(new Hit(Outcome.GUARDED, false, false, false, false), ON));
        assertEquals(List.of(), MeleeSoundRules.hit(new Hit(Outcome.UNAFFECTED, false, false, false, false), ON));
        assertEquals(List.of(), MeleeSoundRules.hit(swordHit(Outcome.UNAFFECTED, true, true, true), ON));
    }

    @Test
    void everyOutcomeHasAtMostOneCue() {
        for (Outcome o : Outcome.values()) {
            for (int bits = 0; bits < 16; bits++) {
                Hit h = new Hit(o, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0);
                assertTrue(MeleeSoundRules.hit(h, ON).size() <= 1, h::toString);
            }
        }
    }

    @Test
    void disabledPlaysNothing() {
        assertEquals(List.of(), attackCues(AttackKind.SLASH, CombatState.fresh(P.staminaMax()), OFF, -1));
        assertEquals(List.of(), attackCues(AttackKind.THRUST, CombatState.fresh(P.staminaMax()), OFF, -1));
        for (Outcome o : Outcome.values()) {
            assertEquals(List.of(), MeleeSoundRules.hit(swordHit(o, true, true, true), OFF), o::name);
            assertEquals(List.of(), MeleeSoundRules.hit(swordHit(o, false, false, false), OFF), o::name);
        }
        assertEquals(List.of(), MeleeSoundRules.unsheathe(OFF));
        assertEquals(List.of(MeleeSoundRules.UNSHEATHE), MeleeSoundRules.unsheathe(ON));
    }

    @Test
    void pitchVariesByAtMostFivePercent() {
        for (Cue c : MeleeSoundRules.ALL) {
            double centre = (c.minPitch() + c.maxPitch()) / 2.0;
            assertTrue(c.minPitch() >= centre * 0.95 - 1e-6 && c.maxPitch() <= centre * 1.05 + 1e-6,
                    () -> "pitch range wider than ±5 %: " + c);
        }
    }

    @Test
    void volumeAndPitchStayInTheirRangesAndVolumeScales() {
        assertEquals(9, MeleeSoundRules.ALL.size());
        for (Cue c : MeleeSoundRules.ALL) {
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
        assertEquals(0.7f, MeleeSoundRules.MISS_SLASH.volume(0.0, 1.0), 1e-6);
        assertEquals(0.8f, MeleeSoundRules.MISS_SLASH.volume(1.0, 1.0), 1e-6);
        assertEquals(0.84f, MeleeSoundRules.MISS_THRUST.pitch(1.0), 1e-6);
    }
}

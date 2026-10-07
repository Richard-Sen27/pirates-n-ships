package com.richardsenger.piratesnships.combat.melee.resolve;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HitResolverTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;
    static final WeaponDefinition A_W = DefaultWeapons.CUTLASS;
    static final WeaponDefinition D_W = DefaultWeapons.SABER;
    static final CombatState FRESH = CombatState.fresh(P.staminaMax());

    static CombatState ok(InputResult r) {
        assertTrue(r.accepted(), () -> "refused: " + r.refusal());
        return r.state();
    }

    static CombatState ticks(CombatState s, int n) {
        for (int i = 0; i < n; i++) s = CombatRules.tick(s, D_W, P);
        return s;
    }

    static CombatState attacking() {
        CombatState s = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, A_W, P));
        for (int i = 0; i < A_W.slash().windupTicks(); i++) s = CombatRules.tick(s, A_W, P);
        assertEquals(Phase.ACTIVE, s.phase());
        return s;
    }

    static IncomingHit slash(boolean frontal) {
        return IncomingHit.modMelee(A_W, AttackKind.SLASH, false, frontal, P);
    }

    // --- Parry timing -------------------------------------------------------------------------------------------

    @Test
    void parryTimingAtEveryOffset() {
        int w = 7;
        for (int off = -6; off <= 10; off++) {
            ParryTiming noAllowance = ParryTiming.classify(off, w, 0);
            ParryTiming allowance2 = ParryTiming.classify(off, w, 2);
            assertEquals(off < 0 ? ParryTiming.TOO_LATE : off < w ? ParryTiming.SUCCESS : ParryTiming.TOO_EARLY, noAllowance, "offset " + off);
            assertEquals(off < -2 ? ParryTiming.TOO_LATE : off < w ? ParryTiming.SUCCESS : ParryTiming.TOO_EARLY, allowance2, "offset " + off);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void hitDuringWindowIsParriedAtEveryTick(int ticksAfterParry) {
        CombatState def = ticks(ok(CombatRules.parry(FRESH, D_W, P)), ticksAfterParry);
        assertEquals(Phase.PARRYING, def.phase());
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, P);
        assertEquals(HitResult.Outcome.PARRIED, r.outcome());
        assertEquals(0f, r.damage());
        assertEquals(Phase.STAGGERED, r.attacker().phase());
        assertEquals(P.parryStaggerTicks(), r.attacker().duration());
        assertNull(r.attacker().attack(), "the parried attack is cancelled");
        assertEquals(P.riposteWindowTicks(), r.defender().riposteTicks());
        assertFalse(r.defender().lockedOut());
        assertEquals(Phase.IDLE, r.defender().phase());
        assertEquals(P.staminaMax(), r.defender().stamina(), 1e-4, "a successful parry is free");
    }

    @Test
    void parryTooEarlyMeansFullHitAndLockout() {
        CombatState def = ticks(ok(CombatRules.parry(FRESH, D_W, P)), P.parryWindowTicks());
        assertEquals(Phase.IDLE, def.phase(), "window closed");
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, P);
        assertEquals(HitResult.Outcome.HIT, r.outcome());
        assertEquals(A_W.slash().damage(), r.damage(), 1e-4);
        assertTrue(r.defender().lockedOut());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void lateParryWithinAllowanceSucceeds(int age) {
        CombatState def = ok(CombatRules.parry(FRESH, D_W, P)); // opened now, the hit landed `age` ticks ago
        assertEquals(HitResult.Outcome.PARRIED, HitResolver.resolve(attacking(), def, D_W, slash(true), age, P).outcome());
    }

    @Test
    void lateParryBeyondAllowanceFailsWithLockout() {
        CombatState def = ok(CombatRules.parry(FRESH, D_W, P));
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), P.latencyAllowanceTicks() + 1, P);
        assertEquals(HitResult.Outcome.HIT, r.outcome());
        assertTrue(r.defender().lockedOut());
        assertEquals(P.staminaMax() - D_W.parry().failedStaminaCost(), r.defender().stamina(), 1e-4);
        MeleeParams noAllowance = P.withParryTiming(7, 0, 15);
        assertEquals(HitResult.Outcome.HIT, HitResolver.resolve(attacking(), def, D_W, slash(true), 1, noAllowance).outcome());
    }

    @Test
    void vanillaMeleeIsParriedButGetsNoLatencyAllowance() {
        CombatState def = ok(CombatRules.parry(FRESH, D_W, P));
        HitResult r = HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(5f, true), 0, P);
        assertEquals(HitResult.Outcome.PARRIED, r.outcome());
        assertEquals(Phase.STAGGERED, r.attacker().phase());
        assertEquals(HitResult.Outcome.HIT, HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(5f, true), 1, P).outcome());
    }

    @Test
    void riposteCannotBeParriedAndBreaksTheParry() {
        CombatState def = ok(CombatRules.parry(FRESH, D_W, P));
        IncomingHit riposte = IncomingHit.modMelee(A_W, AttackKind.SLASH, true, true, P);
        HitResult r = HitResolver.resolve(attacking(), def, D_W, riposte, 0, P);
        assertEquals(HitResult.Outcome.HIT, r.outcome());
        assertTrue(r.defender().lockedOut());
        assertEquals(A_W.slash().damage() * P.riposteDamageBonus() * A_W.parry().riposteMultiplier(), riposte.damage(), 1e-4);
    }

    @Test
    void riposteBonusUsesWeaponMultiplier() {
        WeaponDefinition r = DefaultWeapons.RAPIER;
        assertEquals(r.thrust().damage() * 1.5 * 1.2, IncomingHit.modMelee(r, AttackKind.THRUST, true, true, P).damage(), 1e-4);
        assertEquals(r.thrust().damage(), IncomingHit.modMelee(r, AttackKind.THRUST, false, true, P).damage(), 1e-4);
    }

    @Test
    void hitFromBehindCannotBeParriedOrGuarded() {
        CombatState parrying = ok(CombatRules.parry(FRESH, D_W, P));
        assertEquals(HitResult.Outcome.HIT, HitResolver.resolve(attacking(), parrying, D_W, slash(false), 0, P).outcome());
        CombatState guarding = ok(CombatRules.guardDown(FRESH, D_W, P));
        HitResult g = HitResolver.resolve(attacking(), guarding, D_W, slash(false), 0, P);
        assertEquals(HitResult.Outcome.HIT, g.outcome());
        assertEquals(A_W.slash().damage(), g.damage(), 1e-4);
    }

    @Test
    void projectilesAndOtherDamageAreUnaffected() {
        CombatState parrying = ok(CombatRules.parry(FRESH, D_W, P));
        CombatState guarding = ok(CombatRules.guardDown(FRESH, D_W, P));
        for (CombatState def : new CombatState[]{parrying, guarding}) {
            for (IncomingHit hit : new IncomingHit[]{IncomingHit.projectile(6f), IncomingHit.other(6f)}) {
                HitResult r = HitResolver.resolve(FRESH, def, D_W, hit, 0, P);
                assertEquals(HitResult.Outcome.UNAFFECTED, r.outcome());
                assertEquals(6f, r.damage());
                assertSame(def, r.defender());
            }
        }
    }

    // --- Guard --------------------------------------------------------------------------------------------------

    // P9: a successful guard absorbs the whole frontal hit (melee.guard_absorbs_all, default on); the per-weapon
    // reduction still applies to guard breaks and with the toggle off.

    /** Expected cost of one blocked hit, written out independently of {@link HitResolver#guardCost}. */
    static float blockCost(float damage, float absorbed, MeleeParams p) {
        var g = D_W.guard();
        return (float) ((g.staminaPerHit() + g.staminaPerDamage() * damage + p.guardAbsorbStaminaPerDamage() * absorbed)
                * p.staminaCostMultiplier());
    }

    @Test
    void absorbSurchargeScalesWithTheHit() {
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, P));
        assertEquals(1.0, P.guardAbsorbStaminaPerDamage(), 1e-9, "default");
        HitResult light = HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(2f, true), 0, P);
        HitResult heavy = HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(8f, true), 0, P);
        float lightCost = def.stamina() - light.defender().stamina();
        float heavyCost = def.stamina() - heavy.defender().stamina();
        // 6 more damage: weapon per-damage cost plus 1.0 surcharge per absorbed point
        assertEquals(6f * (D_W.guard().staminaPerDamage() + 1.0f), heavyCost - lightCost, 1e-3, "a heavier hit drains more");
        assertEquals(blockCost(8f, 8f, P), heavyCost, 1e-3);
        assertEquals(D_W.guard().staminaPerHit() + 8f * D_W.guard().staminaPerDamage() + 1.0f * 8f, heavyCost, 1e-3);
    }

    @Test
    void zeroAbsorbSurchargeRestoresTheWeaponCost() {
        MeleeParams flat = P.withGuardAbsorbStaminaPerDamage(0);
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, flat));
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, flat);
        float dmg = A_W.slash().damage();
        assertEquals(0f, r.damage());
        assertEquals(flat.staminaMax() - (D_W.guard().staminaPerHit() + D_W.guard().staminaPerDamage() * dmg), r.defender().stamina(), 1e-3);
    }

    @Test
    void absorbSurchargeCanBreakTheGuard() {
        float dmg = A_W.slash().damage();
        float weaponOnly = D_W.guard().staminaPerHit() + D_W.guard().staminaPerDamage() * dmg;
        // enough for the weapon's cost, not for the surcharge on top
        CombatState def = new CombatState(Phase.GUARDING, null, 0, 0, weaponOnly + 1f, 0, 0, 0, true, false, java.util.Set.of());
        assertEquals(HitResult.Outcome.GUARDED, HitResolver.resolve(attacking(), def, D_W, slash(true), 0,
                P.withGuardAbsorbStaminaPerDamage(0)).outcome());
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, P);
        assertEquals(HitResult.Outcome.GUARD_BROKEN, r.outcome());
        assertEquals(dmg * (1 - D_W.guard().damageReduction()), r.damage(), 1e-4);
    }

    @Test
    void guardAbsorbsFrontalHitAndCostsStamina() {
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, P));
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, P);
        float dmg = A_W.slash().damage();
        assertEquals(HitResult.Outcome.GUARDED, r.outcome());
        assertEquals(0f, r.damage(), "P9: a held guard absorbs the whole hit");
        assertEquals(P.staminaMax() - blockCost(dmg, dmg, P), r.defender().stamina(), 1e-3);
        assertEquals(Phase.GUARDING, r.defender().phase());
        assertFalse(r.defenderStaggered());
        assertEquals(attacking(), r.attacker(), "a guard does not stagger the attacker");
    }

    @Test
    void guardOnlyReducesDamageWithToggleOff() {
        MeleeParams off = P.withGuardAbsorbsAll(false);
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, off));
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, off);
        float dmg = A_W.slash().damage();
        assertEquals(HitResult.Outcome.GUARDED, r.outcome());
        assertEquals(dmg * (1 - D_W.guard().damageReduction()), r.damage(), 1e-4);
        // only the reduction is absorbed, so only that much pays the absorb surcharge
        assertEquals(off.staminaMax() - blockCost(dmg, (float) (dmg * D_W.guard().damageReduction()), off), r.defender().stamina(), 1e-3);
        assertFalse(r.defenderStaggered());
    }

    @Test
    void vanillaMeleeIsGuarded() {
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, P));
        HitResult r = HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(4f, true), 0, P);
        assertEquals(HitResult.Outcome.GUARDED, r.outcome());
        assertEquals(0f, r.damage(), "P9: vanilla melee against a held guard is absorbed");
        assertEquals(P.staminaMax() - blockCost(4f, 4f, P), r.defender().stamina(), 1e-3);
        // toggle off: reduced
        assertEquals(4f * (1 - D_W.guard().damageReduction()),
                HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(4f, true), 0, P.withGuardAbsorbsAll(false)).damage(), 1e-4);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void guardIgnoresHitsFromBehind(boolean absorbsAll) {
        MeleeParams p = P.withGuardAbsorbsAll(absorbsAll);
        CombatState def = ok(CombatRules.guardDown(FRESH, D_W, p));
        HitResult sword = HitResolver.resolve(attacking(), def, D_W, slash(false), 0, p);
        assertEquals(HitResult.Outcome.HIT, sword.outcome());
        assertEquals(A_W.slash().damage(), sword.damage(), 1e-4);
        assertEquals(def.stamina(), sword.defender().stamina(), 1e-4, "no guard cost or absorb surcharge for a hit from behind");
        HitResult vanilla = HitResolver.resolve(FRESH, def, D_W, IncomingHit.vanillaMelee(4f, false), 0, p);
        assertEquals(HitResult.Outcome.HIT, vanilla.outcome());
        assertEquals(4f, vanilla.damage(), 1e-4);
        assertEquals(def.stamina(), vanilla.defender().stamina(), 1e-4, "vanilla hit from behind drains nothing");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void guardBreaksWhenStaminaRunsOut(boolean absorbsAll) {
        MeleeParams p = P.withGuardAbsorbsAll(absorbsAll);
        CombatState def = new CombatState(Phase.GUARDING, null, 0, 0, 5f, 0, 0, 0, true, false, java.util.Set.of());
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, p);
        assertEquals(HitResult.Outcome.GUARD_BROKEN, r.outcome());
        assertTrue(r.defenderStaggered());
        assertEquals(Phase.STAGGERED, r.defender().phase());
        assertEquals(p.guardBreakStaggerTicks(), r.defender().duration());
        assertEquals(0f, r.defender().stamina());
        // P9: a broken guard lets the hit through with the weapon's guard reduction, whatever the toggle
        assertEquals(A_W.slash().damage() * (1 - D_W.guard().damageReduction()), r.damage(), 1e-4);
    }

    // --- Stagger and poise --------------------------------------------------------------------------------------

    @Test
    void thrustIntoRecoveryStaggers() {
        CombatState recovering = ok(CombatRules.startAttack(FRESH, AttackKind.SLASH, D_W, P));
        recovering = ticks(recovering, D_W.slash().windupTicks() + D_W.slash().activeTicks());
        assertEquals(Phase.RECOVERY, recovering.phase());
        IncomingHit thrust = IncomingHit.modMelee(DefaultWeapons.CUTLASS, AttackKind.THRUST, false, true, P);
        assertTrue(thrust.damage() < D_W.poise(), "below poise, so only the recovery rule staggers");
        HitResult r = HitResolver.resolve(FRESH, recovering, D_W, thrust, 0, P);
        assertTrue(r.defenderStaggered());
        assertEquals(P.staggerTicks(), r.defender().duration());
        // a slash into recovery does not
        assertFalse(HitResolver.resolve(FRESH, recovering, D_W, slash(true), 0, P).defenderStaggered());
        // a thrust into an idle defender does not
        assertFalse(HitResolver.resolve(FRESH, FRESH, D_W, thrust, 0, P).defenderStaggered());
    }

    @Test
    void hitReachingPoiseStaggersAndZeroStaminaLowersPoise() {
        IncomingHit heavy = new IncomingHit(HitKind.MOD_MELEE, AttackKind.SLASH, D_W.poise(), false, true);
        assertTrue(HitResolver.resolve(FRESH, FRESH, D_W, heavy, 0, P).defenderStaggered());
        IncomingHit medium = new IncomingHit(HitKind.MOD_MELEE, AttackKind.SLASH, D_W.poise() * 0.6f, false, true);
        assertFalse(HitResolver.resolve(FRESH, FRESH, D_W, medium, 0, P).defenderStaggered());
        CombatState exhausted = new CombatState(Phase.IDLE, null, 0, 0, 0f, 0, 0, 0, false, false, java.util.Set.of());
        assertTrue(HitResolver.resolve(FRESH, exhausted, D_W, medium, 0, P).defenderStaggered(), "staggered more easily at zero stamina");
    }

    @Test
    void unarmedDefenderTakesFullHitWithoutPoise() {
        HitResult r = HitResolver.resolve(attacking(), FRESH, null, new IncomingHit(HitKind.MOD_MELEE, AttackKind.SLASH, 50f, false, true), 0, P);
        assertEquals(HitResult.Outcome.HIT, r.outcome());
        assertEquals(50f, r.damage());
        assertFalse(r.defenderStaggered());
    }

    @Test
    void skillBasedOffChangesNothing() {
        CombatState def = ok(CombatRules.parry(FRESH, D_W, P));
        HitResult r = HitResolver.resolve(attacking(), def, D_W, slash(true), 0, P.withSkillBased(false));
        assertEquals(HitResult.Outcome.UNAFFECTED, r.outcome());
        assertEquals(A_W.slash().damage(), r.damage());
    }
}

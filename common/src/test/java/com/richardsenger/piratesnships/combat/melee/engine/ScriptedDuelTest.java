package com.richardsenger.piratesnships.combat.melee.engine;

import com.richardsenger.piratesnships.combat.melee.geometry.Box;
import com.richardsenger.piratesnships.combat.melee.geometry.Vec;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult.Outcome;
import com.richardsenger.piratesnships.combat.melee.resolve.IncomingHit;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.InputResult;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** A whole exchange, tick by tick, through the same engine the server uses. */
class ScriptedDuelTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;

    static final class F implements MeleeEngine.Fighter {
        final int id;
        final WeaponDefinition weapon;
        final Vec feet;
        final Vec look;
        CombatState state = CombatState.fresh(P.staminaMax());
        float damageTaken;

        F(int id, WeaponDefinition weapon, Vec feet, Vec look) {
            this.id = id;
            this.weapon = weapon;
            this.feet = feet;
            this.look = look;
        }

        @Override public int id() { return id; }
        @Override public CombatState state() { return state; }
        @Override public void setState(CombatState s) { state = s; }
        @Override public WeaponDefinition weapon() { return weapon; }
        @Override public Vec eye() { return feet.add(new Vec(0, 1.6, 0)); }
        @Override public Vec look() { return look; }
        @Override public Box box() { return Box.standing(feet, 0.6, 1.8); }
        @Override public boolean valid() { return true; }

        Refusal input(Function<CombatState, InputResult> rule) {
            InputResult r = rule.apply(state);
            state = r.state();
            return r.refusal();
        }
    }

    record Event(long tick, int attacker, int target, Outcome outcome, float damage, boolean staggered) {
    }

    final F a = new F(1, DefaultWeapons.RAPIER, new Vec(0, 0, 0), new Vec(1, 0, 0));
    final F b = new F(2, DefaultWeapons.CUTLASS, new Vec(2, 0, 0), new Vec(-1, 0, 0));
    final List<F> fighters = List.of(a, b);
    final MeleeEngine engine = new MeleeEngine();
    final List<Event> log = new ArrayList<>();
    long now;

    final MeleeEngine.Arena arena = new MeleeEngine.Arena() {
        @Override
        public List<? extends MeleeEngine.Fighter> candidates(MeleeEngine.Fighter attacker, double range) {
            return fighters;
        }

        @Override
        public void apply(MeleeEngine.Fighter attacker, MeleeEngine.Fighter target, IncomingHit hit, HitResult r) {
            ((F) target).damageTaken += r.damage();
            log.add(new Event(now, attacker.id(), target.id(), r.outcome(), r.damage(), r.defenderStaggered()));
        }
    };

    void runTo(long tick) {
        while (now < tick) {
            engine.tick(now, fighters, arena, P);
            now++;
        }
    }

    @Test
    void fullExchange() {
        // t0: A thrusts (rapier wind-up 6, active from tick 6); t4: B parries, the thrust lands 2 ticks into the window
        assertEquals(Refusal.NONE, a.input(s -> CombatRules.startAttack(s, AttackKind.THRUST, a.weapon, P)));
        runTo(4);
        assertEquals(Phase.WINDUP, a.state.phase());
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.parry(s, b.weapon, P)));
        runTo(7);
        assertEquals(new Event(6, 1, 2, Outcome.PARRIED, 0f, false), log.get(0));
        assertEquals(Phase.STAGGERED, a.state.phase());
        assertTrue(b.state.riposteReady());
        assertEquals(Refusal.STAGGERED, a.input(s -> CombatRules.parry(s, a.weapon, P)));

        // t7: B ripostes (cutlass wind-up 5): A is staggered and can't defend; riposte bonus applies
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.startAttack(s, AttackKind.SLASH, b.weapon, P)));
        assertTrue(b.state.riposteAttack());
        runTo(30);
        float riposte = (float) (7.0 * P.riposteDamageBonus() * 1.0);
        assertEquals(new Event(12, 2, 1, Outcome.HIT, riposte, true), log.get(1), "riposte beats the rapier's poise");
        assertEquals(2, log.size(), "the slash hits each target once");

        // t40: A guards, t41: B slashes; the hit is held for the latency allowance, then guarded
        runTo(40);
        assertEquals(Phase.IDLE, a.state.phase());
        assertEquals(Refusal.NONE, a.input(s -> CombatRules.guardDown(s, a.weapon, P)));
        runTo(41);
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.startAttack(s, AttackKind.SLASH, b.weapon, P)));
        runTo(47);
        assertEquals(1, engine.heldHits(), "hit on a defender who could still parry is held");
        runTo(55);
        assertEquals(new Event(46 + P.latencyAllowanceTicks(), 2, 1, Outcome.GUARDED, 3.5f, false), log.get(2));
        assertEquals(Phase.GUARDING, a.state.phase());

        // t60: B slashes, the hit lands at 65; A's parry input arrives one tick late (66): inside the allowance
        runTo(60);
        a.input(CombatRules::guardUp);
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.startAttack(s, AttackKind.SLASH, b.weapon, P)));
        runTo(66);
        assertEquals(3, log.size());
        assertEquals(Refusal.NONE, a.input(s -> CombatRules.parry(s, a.weapon, P)));
        runTo(67);
        assertEquals(new Event(66, 2, 1, Outcome.PARRIED, 0f, false), log.get(3));
        assertEquals(Phase.STAGGERED, b.state.phase());

        // t100: A parries far too early: the window closes (failed), the lockout blocks the next parry, B's hit lands
        runTo(100);
        float before = a.state.stamina();
        assertEquals(Refusal.NONE, a.input(s -> CombatRules.parry(s, a.weapon, P)));
        runTo(105);
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.startAttack(s, AttackKind.SLASH, b.weapon, P)));
        runTo(108);
        assertTrue(a.state.lockedOut());
        assertEquals(before - a.weapon.parry().failedStaminaCost(), a.state.stamina(), 1e-3);
        assertEquals(Refusal.LOCKED_OUT, a.input(s -> CombatRules.parry(s, a.weapon, P)));
        runTo(111);
        assertEquals(new Event(110, 2, 1, Outcome.HIT, 7f, false), log.get(4), "lockout: no hold, full hit, below poise");

        // t130: B slashes, t132: A thrusts into B's recovery and staggers B
        runTo(130);
        assertEquals(Refusal.NONE, b.input(s -> CombatRules.startAttack(s, AttackKind.SLASH, b.weapon, P)));
        runTo(132);
        assertEquals(Refusal.NONE, a.input(s -> CombatRules.startAttack(s, AttackKind.THRUST, a.weapon, P)));
        runTo(140);
        assertEquals(new Event(135, 2, 1, Outcome.HIT, 7f, false), log.get(5), "A was winding up and couldn't parry");
        assertEquals(new Event(138, 1, 2, Outcome.HIT, 9f, true), log.get(6), "thrust into recovery staggers");
        assertEquals(Phase.STAGGERED, b.state.phase());
        assertEquals(7, log.size());
        assertEquals(riposte + 3.5f + 7f + 7f, a.damageTaken, 1e-3);
        assertEquals(9f, b.damageTaken, 1e-3);
    }

    @Test
    void parriedSlashStopsMidSweepAndThrustHitsFirstOnly() {
        F c = new F(3, DefaultWeapons.SABER, new Vec(1.8, 0, 1.2), new Vec(-1, 0, 0));
        List<F> three = List.of(a, b, c);
        MeleeEngine.Arena arena3 = new MeleeEngine.Arena() {
            @Override public List<? extends MeleeEngine.Fighter> candidates(MeleeEngine.Fighter attacker, double range) { return three; }
            @Override public void apply(MeleeEngine.Fighter at, MeleeEngine.Fighter t, IncomingHit hit, HitResult r) {
                log.add(new Event(now, at.id(), t.id(), r.outcome(), r.damage(), r.defenderStaggered()));
            }
        };
        MeleeParams noHold = P.withParryTiming(7, 0, 15);
        b.input(s -> CombatRules.parry(s, b.weapon, noHold));
        c.input(s -> CombatRules.guardDown(s, c.weapon, noHold));
        F swinger = new F(4, DefaultWeapons.CUTLASS, new Vec(0, 0, 0), new Vec(1, 0, 0));
        swinger.state = CombatRules.startAttack(swinger.state, AttackKind.SLASH, swinger.weapon, noHold).state();
        List<F> all = List.of(swinger, b, c);
        MeleeEngine.Arena arenaAll = new MeleeEngine.Arena() {
            @Override public List<? extends MeleeEngine.Fighter> candidates(MeleeEngine.Fighter attacker, double range) { return all; }
            @Override public void apply(MeleeEngine.Fighter at, MeleeEngine.Fighter t, IncomingHit hit, HitResult r) { arena3.apply(at, t, hit, r); }
        };
        for (now = 0; now < 10; now++) engine.tick(now, all, arenaAll, noHold);
        // b (nearest) parries; the swing stops before reaching c
        assertEquals(1, log.size());
        assertEquals(Outcome.PARRIED, log.get(0).outcome());
        assertEquals(2, log.get(0).target());
    }

    @Test
    void skillBasedOffEngineDoesNothing() {
        a.state = CombatRules.startAttack(a.state, AttackKind.SLASH, a.weapon, P).state();
        CombatState before = a.state;
        engine.tick(0, fighters, arena, P.withSkillBased(false));
        assertSame(before, a.state);
    }
}

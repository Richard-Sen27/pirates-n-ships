package com.richardsenger.piratesnships.combat.melee.engine;

import com.richardsenger.piratesnships.combat.melee.geometry.Box;
import com.richardsenger.piratesnships.combat.melee.geometry.HitGeometry;
import com.richardsenger.piratesnships.combat.melee.geometry.Vec;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResolver;
import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.resolve.IncomingHit;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.weapon.WeaponDefinition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Runs melee for a set of combatants, one server tick at a time, without world access: the integration layer wraps
 * real entities as {@link Fighter}s and supplies candidates and damage through an {@link Arena}. JUnit drives the
 * same engine with plain objects (scripted duels).
 *
 * <p>Per {@link #tick}: (1) held hits are resolved, (2) every attacker in its active phase sweeps for new targets,
 * (3) every fighter's state advances by one tick.
 *
 * <p><b>Latency allowance:</b> a mod melee hit on a defender who could parry right now (idle or guarding, not locked
 * out, stamina left, weapon in hand) and who faces the attacker is <i>held</i> for {@code latencyAllowanceTicks}
 * instead of landing at once. If the defender opens a parry while it is held, the hit is parried (the parry input
 * was late only because of network delay); otherwise it lands when the allowance runs out, against the defender's
 * state at that moment. With an allowance of 0 hits land immediately. Vanilla hits are never held.
 */
public final class MeleeEngine {

    /** One combatant. Implementations must keep {@link #state()} and {@link #setState} consistent. */
    public interface Fighter {
        int id();

        CombatState state();

        void setState(CombatState state);

        /** The weapon in hand, or {@code null} (can't attack, guard or parry). */
        @Nullable WeaponDefinition weapon();

        Vec eye();

        Vec look();

        Box box();

        /** Still alive and present; invalid fighters are skipped and their held hits dropped. */
        boolean valid();
    }

    public interface Arena {
        /** Possible targets within {@code range} of the attacker (the attacker itself may be included; it is skipped). */
        List<? extends Fighter> candidates(Fighter attacker, double range);

        /** Apply a resolved hit: damage, effects. States are already updated. */
        void apply(Fighter attacker, Fighter target, IncomingHit hit, HitResult result);
    }

    private record HeldHit(Fighter attacker, Fighter target, IncomingHit hit, long tick) {
    }

    private final List<HeldHit> held = new ArrayList<>();

    public int heldHits() {
        return held.size();
    }

    public void clear() {
        held.clear();
    }

    public void tick(long now, Collection<? extends Fighter> fighters, Arena arena, MeleeParams p) {
        if (!p.skillBased()) {
            held.clear();
            return;
        }
        processHeld(now, arena, p);
        for (Fighter f : fighters) {
            if (f.valid() && f.state().phase() == Phase.ACTIVE) sweep(now, f, arena, p);
        }
        for (Fighter f : fighters) {
            if (f.valid()) f.setState(CombatRules.tick(f.state(), f.weapon(), p));
        }
    }

    /** Resolve held hits whose defender opened a parry, or whose allowance ran out. */
    private void processHeld(long now, Arena arena, MeleeParams p) {
        for (Iterator<HeldHit> it = held.iterator(); it.hasNext(); ) {
            HeldHit h = it.next();
            if (!h.target.valid() || !h.attacker.valid()) {
                it.remove();
                continue;
            }
            int age = (int) (now - h.tick);
            if (h.target.state().phase() == Phase.PARRYING || age >= p.latencyAllowanceTicks()) {
                it.remove();
                resolve(h.attacker, h.target, h.hit, age, arena, p);
            }
        }
    }

    private void sweep(long now, Fighter attacker, Arena arena, MeleeParams p) {
        WeaponDefinition w = attacker.weapon();
        CombatState s = attacker.state();
        AttackKind kind = s.attack();
        if (w == null || kind == null) return;
        if (kind == AttackKind.THRUST && s.attackHit()) return; // a thrust hits its first target only
        List<HitGeometry.Target<Fighter>> targets = new ArrayList<>();
        for (Fighter c : arena.candidates(attacker, w.reach(kind))) {
            if (c != attacker && c.id() != attacker.id() && c.valid() && !s.hitTargets().contains(c.id())) {
                targets.add(new HitGeometry.Target<>(c, c.box()));
            }
        }
        List<Fighter> hits = kind == AttackKind.SLASH
                ? HitGeometry.slash(attacker.eye(), attacker.look(), w.slash().reach(), w.slash().arcDegrees(), w.slash().verticalTolerance(), targets)
                : HitGeometry.thrust(attacker.eye(), attacker.look(), w.thrust().reach(), w.thrust().thickness(), targets).map(List::of).orElse(List.of());
        for (Fighter target : hits) {
            if (attacker.state().phase() != Phase.ACTIVE) break; // parried mid-sweep: the swing stops
            attacker.setState(attacker.state().withHitTarget(target.id()));
            WeaponDefinition tw = target.weapon();
            boolean frontal = HitGeometry.inFront(target.eye(), target.look(), attacker.eye(), tw == null ? 360.0 : tw.guard().arcDegrees());
            IncomingHit hit = IncomingHit.modMelee(w, kind, s.riposteAttack(), frontal, p);
            if (p.latencyAllowanceTicks() > 0 && hit.parryable() && CombatRules.canParry(target.state(), tw, p)) {
                held.add(new HeldHit(attacker, target, hit, now));
            } else {
                resolve(attacker, target, hit, 0, arena, p);
            }
        }
    }

    private static void resolve(Fighter attacker, Fighter target, IncomingHit hit, int age, Arena arena, MeleeParams p) {
        HitResult r = HitResolver.resolve(attacker.state(), target.state(), target.weapon(), hit, age, p);
        attacker.setState(r.attacker());
        target.setState(r.defender());
        arena.apply(attacker, target, hit, r);
    }

    /** Convenience for callers that only need the first fighter matching an id. */
    public static Optional<Fighter> byId(Collection<? extends Fighter> fighters, int id) {
        for (Fighter f : fighters) if (f.id() == id) return Optional.of(f);
        return Optional.empty();
    }
}

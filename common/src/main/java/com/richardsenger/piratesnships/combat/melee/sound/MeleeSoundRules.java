package com.richardsenger.piratesnships.combat.melee.sound;

import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pure mapping from melee events to sound cues (docs/design.md §8.5, §16), without world access.
 * {@link MeleeSoundPlayer} turns the cues into real sounds at the right entity.
 *
 * <p>One sound per attack, decided by its outcome at the moment it resolves (P8): the wind-up and the start of the
 * hit frames are silent; an attack that lands sounds at its target, one that ends its hit frames without touching
 * anyone whooshes at the attacker.
 *
 * <table>
 *   <tr><th>Outcome</th><th>Sound</th><th>At</th><th>Volume</th><th>Pitch</th></tr>
 *   <tr><td>miss: hit frames end, nothing hit, slash</td><td>miss</td><td>attacker</td><td>0.7–0.8</td><td>0.95–1.05</td></tr>
 *   <tr><td>miss, thrust</td><td>miss</td><td>attacker</td><td>0.7–0.8</td><td>0.76–0.84</td></tr>
 *   <tr><td>sword lands on flesh (slash)</td><td>hit</td><td>target</td><td>0.9–1.0</td><td>0.95–1.05</td></tr>
 *   <tr><td>sword lands on flesh: thrust, riposte or staggering hit</td><td>hit_heavy</td><td>target</td><td>1.0</td><td>0.95–1.05</td></tr>
 *   <tr><td>sword lands on a chestplate</td><td>hit_armor</td><td>target</td><td>0.9–1.0</td><td>0.95–1.05</td></tr>
 *   <tr><td>sword lands on a defender mid-attack with a mod sword (blade meets blade)</td><td>clash</td><td>target</td><td>0.8–0.9</td><td>0.95–1.05</td></tr>
 *   <tr><td>parry (sword or vanilla melee)</td><td>clash</td><td>defender</td><td>1.0</td><td>0.95–1.05</td></tr>
 *   <tr><td>guard absorbs a hit (also when it breaks)</td><td>clash</td><td>defender</td><td>0.55–0.6</td><td>0.80–0.88</td></tr>
 *   <tr><td>a mod sword drawn into the main hand</td><td>unsheathe</td><td>player</td><td>0.8</td><td>0.95–1.05</td></tr>
 * </table>
 *
 * A feint, a stagger and the start of the hit frames have no cue. A vanilla melee hit that lands gets no cue (vanilla
 * plays its own); parried or guarded it clashes. Every pitch range stays within ±5 % of its centre so repeated hits
 * sound like the same weapon. Every volume is multiplied by {@link Params#volume()}; with {@link Params#enabled()} off
 * nothing plays.
 */
public final class MeleeSoundRules {

    /** The sound events a cue plays; {@link MeleeSoundPlayer} maps them to the {@code combat.melee.*} events. */
    public enum Sound {
        MISS, HIT, HIT_HEAVY, CLASH, HIT_ARMOR, UNSHEATHE
    }

    /** Where a cue plays. {@link #SELF} = the entity whose state changed. */
    public enum At {
        SELF, ATTACKER, DEFENDER
    }

    /** Config view: {@code melee.sounds.enabled} and {@code melee.sounds.volume}. */
    public record Params(boolean enabled, double volume) {
        public static final Params DEFAULTS = new Params(true, 1.0);
    }

    /** One sound to play, with uniform volume and pitch ranges (before {@link Params#volume()}). */
    public record Cue(Sound sound, At at, float minVolume, float maxVolume, float minPitch, float maxPitch) {

        /** Volume for a uniform {@code u} in [0, 1), scaled by the config volume. */
        public float volume(double u, double scale) {
            return (float) ((minVolume + (maxVolume - minVolume) * u) * scale);
        }

        /** Pitch for a uniform {@code u} in [0, 1). */
        public float pitch(double u) {
            return (float) (minPitch + (maxPitch - minPitch) * u);
        }
    }

    /**
     * What one resolved hit looked like, for {@link #hit}.
     *
     * @param outcome     the resolver's outcome
     * @param modMelee    a skill-based sword hit (false: a vanilla melee hit seen by the incoming-damage hook)
     * @param heavy       a thrust, a riposte or a hit that staggered ({@link #heavy})
     * @param bladeOnBlade the defender was mid-attack with a mod sword in hand when the hit landed ({@link #bladeOnBlade})
     * @param armoured    the target wears armour on its chest ({@link MeleeSoundPlayer#armoured})
     */
    public record Hit(HitResult.Outcome outcome, boolean modMelee, boolean heavy, boolean bladeOnBlade, boolean armoured) {
    }

    public static final Cue MISS_SLASH = new Cue(Sound.MISS, At.SELF, 0.7f, 0.8f, 0.95f, 1.05f);
    public static final Cue MISS_THRUST = new Cue(Sound.MISS, At.SELF, 0.7f, 0.8f, 0.76f, 0.84f);
    public static final Cue HIT_FLESH = new Cue(Sound.HIT, At.DEFENDER, 0.9f, 1.0f, 0.95f, 1.05f);
    public static final Cue HIT_HEAVY = new Cue(Sound.HIT_HEAVY, At.DEFENDER, 1.0f, 1.0f, 0.95f, 1.05f);
    public static final Cue HIT_ARMOR = new Cue(Sound.HIT_ARMOR, At.DEFENDER, 0.9f, 1.0f, 0.95f, 1.05f);
    public static final Cue BLADE_CLASH = new Cue(Sound.CLASH, At.DEFENDER, 0.8f, 0.9f, 0.95f, 1.05f);
    public static final Cue PARRY = new Cue(Sound.CLASH, At.DEFENDER, 1.0f, 1.0f, 0.95f, 1.05f);
    public static final Cue GUARD = new Cue(Sound.CLASH, At.DEFENDER, 0.55f, 0.6f, 0.80f, 0.88f);
    public static final Cue UNSHEATHE = new Cue(Sound.UNSHEATHE, At.SELF, 0.8f, 0.8f, 0.95f, 1.05f);

    /** Every cue, for range checks. */
    public static final List<Cue> ALL = List.of(MISS_SLASH, MISS_THRUST, HIT_FLESH, HIT_HEAVY, HIT_ARMOR, BLADE_CLASH,
            PARRY, GUARD, UNSHEATHE);

    private MeleeSoundRules() {
    }

    /**
     * Cues of one combatant's state change: only the miss, when an attack leaves its hit frames ({@link Phase#ACTIVE})
     * for its recovery without having hit anyone ({@link CombatState#attackHit()}; a hit held for the latency
     * allowance already counts). A parried attacker leaves the hit frames into a stagger and stays silent (the clash at
     * the defender covers it); a feint never reaches the hit frames.
     */
    public static List<Cue> stateChange(CombatState before, CombatState after, Params p) {
        if (!p.enabled()) return List.of();
        if (before.phase() == Phase.ACTIVE && after.phase() == Phase.RECOVERY && before.attack() != null
                && !before.attackHit() && !after.attackHit()) {
            return List.of(before.attack() == AttackKind.THRUST ? MISS_THRUST : MISS_SLASH);
        }
        return List.of();
    }

    /** A landed hit sounds heavy when it is a thrust, a riposte or staggers the target. */
    public static boolean heavy(@Nullable AttackKind attack, boolean riposte, boolean staggered) {
        return attack == AttackKind.THRUST || riposte || staggered;
    }

    /**
     * Blade meets blade: the defender was winding up or in its hit frames ({@code defenderBefore}, its state before the
     * hit was resolved) with a mod sword in hand.
     */
    public static boolean bladeOnBlade(@Nullable CombatState defenderBefore, boolean defenderArmed) {
        if (!defenderArmed || defenderBefore == null) return false;
        return defenderBefore.phase() == Phase.WINDUP || defenderBefore.phase() == Phase.ACTIVE;
    }

    /** The one cue of a resolved hit, or none. */
    public static List<Cue> hit(Hit h, Params p) {
        if (!p.enabled()) return List.of();
        return switch (h.outcome()) {
            case PARRIED -> List.of(PARRY);
            case GUARDED, GUARD_BROKEN -> List.of(GUARD);
            case HIT -> {
                if (!h.modMelee()) yield List.of();
                if (h.bladeOnBlade()) yield List.of(BLADE_CLASH);
                if (h.armoured()) yield List.of(HIT_ARMOR);
                yield List.of(h.heavy() ? HIT_HEAVY : HIT_FLESH);
            }
            case UNAFFECTED -> List.of();
        };
    }

    /** The unsheathe cue, or none when sounds are off. */
    public static List<Cue> unsheathe(Params p) {
        return p.enabled() ? List.of(UNSHEATHE) : List.of();
    }
}

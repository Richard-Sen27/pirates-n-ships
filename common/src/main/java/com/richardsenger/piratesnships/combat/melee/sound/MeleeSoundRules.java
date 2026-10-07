package com.richardsenger.piratesnships.combat.melee.sound;

import com.richardsenger.piratesnships.combat.melee.resolve.HitResult;
import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure mapping from melee events to sound cues (docs/design.md §8.5, §16), without world access.
 * {@link MeleeSoundPlayer} turns the cues into real sounds at the right entity.
 *
 * <table>
 *   <tr><th>Event</th><th>Sound</th><th>At</th><th>Volume</th><th>Pitch</th></tr>
 *   <tr><td>hit frames begin, slash (also a riposte)</td><td>swing</td><td>attacker</td><td>0.7–0.9</td><td>0.9–1.1</td></tr>
 *   <tr><td>hit frames begin, thrust</td><td>swing</td><td>attacker</td><td>0.7–0.9</td><td>1.1–1.3</td></tr>
 *   <tr><td>feint (wind-up aborted)</td><td>swing</td><td>feinter</td><td>0.25–0.35</td><td>1.5–1.7</td></tr>
 *   <tr><td>sword hit, target without a chestplate</td><td>hit_heavy</td><td>target</td><td>0.9–1.0</td><td>0.9–1.1</td></tr>
 *   <tr><td>sword hit, target wearing a chestplate</td><td>hit_armor</td><td>target</td><td>0.9–1.0</td><td>0.9–1.1</td></tr>
 *   <tr><td>parry (sword or vanilla melee)</td><td>parry</td><td>defender</td><td>1.0</td><td>0.9–1.1</td></tr>
 *   <tr><td>guard absorbs a hit (also when it breaks)</td><td>parry</td><td>defender</td><td>0.5–0.6</td><td>0.7–0.8</td></tr>
 *   <tr><td>defender staggered by a hit or a guard break</td><td>hit_heavy</td><td>defender</td><td>0.6–0.7</td><td>0.5–0.6</td></tr>
 *   <tr><td>a mod sword drawn into the main hand</td><td>unsheathe</td><td>player</td><td>0.8</td><td>0.95–1.05</td></tr>
 * </table>
 *
 * A parried attacker's stagger has no cue of its own (the clash covers it). A vanilla melee hit that lands gets no hit
 * cue (vanilla plays its own); its stagger still does. Every volume is multiplied by {@link Params#volume()}; with
 * {@link Params#enabled()} off nothing plays.
 */
public final class MeleeSoundRules {

    /** The sound files a cue plays; {@link MeleeSoundPlayer} maps them to the {@code combat.melee.*} events. */
    public enum Sound {
        SWING, PARRY, HIT_FLESH, HIT_ARMOR, UNSHEATHE
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

    public static final Cue SWING_SLASH = new Cue(Sound.SWING, At.SELF, 0.7f, 0.9f, 0.9f, 1.1f);
    public static final Cue SWING_THRUST = new Cue(Sound.SWING, At.SELF, 0.7f, 0.9f, 1.1f, 1.3f);
    public static final Cue FEINT = new Cue(Sound.SWING, At.SELF, 0.25f, 0.35f, 1.5f, 1.7f);
    public static final Cue HIT_FLESH = new Cue(Sound.HIT_FLESH, At.DEFENDER, 0.9f, 1.0f, 0.9f, 1.1f);
    public static final Cue HIT_ARMOR = new Cue(Sound.HIT_ARMOR, At.DEFENDER, 0.9f, 1.0f, 0.9f, 1.1f);
    public static final Cue PARRY = new Cue(Sound.PARRY, At.DEFENDER, 1.0f, 1.0f, 0.9f, 1.1f);
    public static final Cue GUARD = new Cue(Sound.PARRY, At.DEFENDER, 0.5f, 0.6f, 0.7f, 0.8f);
    public static final Cue STAGGER = new Cue(Sound.HIT_FLESH, At.DEFENDER, 0.6f, 0.7f, 0.5f, 0.6f);
    public static final Cue UNSHEATHE = new Cue(Sound.UNSHEATHE, At.SELF, 0.8f, 0.8f, 0.95f, 1.05f);

    private MeleeSoundRules() {
    }

    /**
     * Cues of one combatant's state change: the swing when the hit frames begin (entering {@link Phase#ACTIVE}), the
     * feint whoosh when a wind-up is aborted into a feint recovery. Hits, parries, guards and staggers come from
     * {@link #hit} instead, since they are decided by the hit resolution.
     */
    public static List<Cue> stateChange(CombatState before, CombatState after, Params p) {
        if (!p.enabled()) return List.of();
        if (after.phase() == Phase.ACTIVE && before.phase() != Phase.ACTIVE && after.attack() != null) {
            return List.of(after.attack() == AttackKind.THRUST ? SWING_THRUST : SWING_SLASH);
        }
        if (after.feint() && !before.feint()) return List.of(FEINT);
        return List.of();
    }

    /**
     * Cues of one resolved hit.
     *
     * @param modMelee  a skill-based sword hit (false: a vanilla melee hit seen by the incoming-damage hook)
     * @param armoured  the target wears armour on its chest ({@link MeleeSoundPlayer#armoured})
     */
    public static List<Cue> hit(HitResult.Outcome outcome, boolean defenderStaggered, boolean modMelee, boolean armoured,
                                Params p) {
        if (!p.enabled()) return List.of();
        List<Cue> out = new ArrayList<>(2);
        switch (outcome) {
            case PARRIED -> out.add(PARRY);
            case GUARDED, GUARD_BROKEN -> out.add(GUARD);
            case HIT -> {
                if (modMelee) out.add(armoured ? HIT_ARMOR : HIT_FLESH);
            }
            case UNAFFECTED -> {
                return List.of();
            }
        }
        if (defenderStaggered && outcome != HitResult.Outcome.PARRIED) out.add(STAGGER);
        return out;
    }

    /** The unsheathe cue, or none when sounds are off. */
    public static List<Cue> unsheathe(Params p) {
        return p.enabled() ? List.of(UNSHEATHE) : List.of();
    }
}

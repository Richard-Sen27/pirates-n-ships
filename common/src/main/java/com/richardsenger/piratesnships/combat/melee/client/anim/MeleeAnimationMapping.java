package com.richardsenger.piratesnships.combat.melee.client.anim;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pure part of the melee animations (no library, no world): which animation plays for a phase, and how fast and from
 * where, so it stays in step with the server's phase timings (docs/design.md §8.5, docs/animation-libraries.md).
 *
 * <p>Animation names are paths under the mod namespace; the files live in
 * {@code assets/pirates_n_ships/player_animations/} (one animation per file, the animation key equals the name).
 * One animation per phase: its length is stretched to the phase duration the server sends.
 */
public final class MeleeAnimationMapping {

    /** Layer id path ({@code pirates_n_ships:melee}). */
    public static final String LAYER = "melee";

    public static final String SLASH_WINDUP = "slash_windup";
    public static final String SLASH_ACTIVE = "slash_active";
    public static final String SLASH_RECOVERY = "slash_recovery";
    public static final String THRUST_WINDUP = "thrust_windup";
    public static final String THRUST_ACTIVE = "thrust_active";
    public static final String THRUST_RECOVERY = "thrust_recovery";
    /** Wind-up of a riposte (either attack): a quicker, lower flourish so the counter reads differently. */
    public static final String RIPOSTE_WINDUP = "riposte_windup";
    /** Guard down: raise the sword across the body and hold it. */
    public static final String GUARD = "guard";
    /** Guard up (released): lower the sword back to rest. */
    public static final String GUARD_LOWER = "guard_lower";
    public static final String PARRY = "parry";
    public static final String STAGGER = "stagger";

    /** Every animation this mapping can return. */
    public static final List<String> ALL = List.of(SLASH_WINDUP, SLASH_ACTIVE, SLASH_RECOVERY, THRUST_WINDUP,
            THRUST_ACTIVE, THRUST_RECOVERY, RIPOSTE_WINDUP, GUARD, GUARD_LOWER, PARRY, STAGGER);

    /** Speed limits, so a broken weapon file (1-tick phases, huge durations) can't make the animation absurd. */
    public static final float MIN_SPEED = 0.05f;
    public static final float MAX_SPEED = 20f;

    private MeleeAnimationMapping() {
    }

    /**
     * The animation of a phase, or {@code null} for {@link Phase#IDLE} (no combat animation). An attack phase without
     * an attack kind (should not happen) plays the slash.
     */
    public static @Nullable String forPhase(Phase phase, @Nullable AttackKind attack, boolean riposte) {
        boolean thrust = attack == AttackKind.THRUST;
        return switch (phase) {
            case IDLE -> null;
            case WINDUP -> riposte ? RIPOSTE_WINDUP : thrust ? THRUST_WINDUP : SLASH_WINDUP;
            case ACTIVE -> thrust ? THRUST_ACTIVE : SLASH_ACTIVE;
            case RECOVERY -> thrust ? THRUST_RECOVERY : SLASH_RECOVERY;
            case GUARDING -> GUARD;
            case PARRYING -> PARRY;
            case STAGGERED -> STAGGER;
        };
    }

    /**
     * What plays when the entity goes back to idle after {@code previous}: lowering the guard after guarding, nothing
     * otherwise (the attack and stagger animations already end at rest; the layer simply stops).
     */
    public static @Nullable String onStop(@Nullable Phase previous) {
        return previous == Phase.GUARDING ? GUARD_LOWER : null;
    }

    /**
     * Playback speed that makes an animation of {@code animationLengthTicks} last exactly {@code durationTicks}
     * (PAL's {@code SpeedModifier}: 2 = twice as fast). Open-ended phases ({@code durationTicks <= 0}: guard) and
     * animations without a length play at normal speed. Clamped to {@link #MIN_SPEED}..{@link #MAX_SPEED}.
     */
    public static float speed(float animationLengthTicks, int durationTicks) {
        if (durationTicks <= 0 || !(animationLengthTicks > 0f)) return 1f;
        float speed = animationLengthTicks / durationTicks;
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    /**
     * Where to start the animation, in animation ticks, for a phase that already ran {@code elapsedTicks} game ticks
     * (catching up on a packet that arrived late, or a remote player first seen mid-phase). At {@code speed} the
     * animation advances {@code speed} animation ticks per game tick. Clamped to the animation's length.
     */
    public static float startTick(int elapsedTicks, float speed, float animationLengthTicks) {
        float start = Math.max(0, elapsedTicks) * speed;
        return animationLengthTicks > 0f ? Math.min(start, animationLengthTicks) : start;
    }
}
